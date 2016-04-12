package fr.proline.core.service.msq.quantify

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.collection._
import fr.profi.util.ms._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.config._
import fr.proline.core.algo.msq.summarizing._
import fr.proline.core.om.model.msi.LazyResultSummary
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLSpectrumProvider
import fr.proline.core.om.storer.msi.impl.ReadBackRsmDuplicator
import fr.proline.core.om.storer.msi.impl.ResetIdsRsmDuplicator
import fr.proline.core.orm.msi.{ ObjectTree => MsiObjectTree }
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.core.om.storer.msi.impl.ReadBackRsmDuplicator
import fr.proline.core.om.storer.msi.impl.ResetIdsRsmDuplicator

class IsobaricTaggingQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantMethod: IsobaricTaggingQuantMethod,
  val quantConfig: IsobaricTaggingQuantConfig
) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  private val masterQc = experimentalDesign.masterQuantChannels.find(_.number == udsMasterQuantChannel.getNumber).get
  private val quantChannelsByIdentRsmId = masterQc.quantChannels.groupBy( _.identResultSummaryId )
  private val tagById = quantMethod.tagById
  private val tagByQcId = masterQc.quantChannels.toLongMapWith { qc =>
    (qc.id, tagById(qc.quantLabelId.get))
  }
  private val msnMozTolUnit = MassTolUnit.withName( quantConfig.extractionParams.mozTolUnit )
  private val msnMozTolInDaByTagId = quantMethod.quantLabels.toLongMapWith { tag =>
    (tag.id, calcMozTolInDalton(tag.reporterMz, quantConfig.extractionParams.mozTol, msnMozTolUnit) )
  }
  private val maxMassToUse = quantMethod.quantLabels.map(_.reporterMz).max + 1
  
  private val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
  
  protected def quantifyMasterChannel(): Unit = {

    // --- TODO: merge following code with AbstractLabelFreeFeatureQuantifier ---
    // DBO: put in AbstractMasterQuantChannelQuantifier ??? possible conflict with WeightedSpectralCountQuantifier ???

    require( udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require( msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(entityCache.msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRsm = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRsm.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsMasterQuantChannel)

    // Store master quant result summary   
     val quantRsm = if(!isMergedRsmProvided) {
        
        ResetIdsRsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRsm, msiQuantResultSet, msiEm) 
      } else {
        val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)
        val rsmDuplicator = new ReadBackRsmDuplicator(rsmProvider)
        rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRsm, msiQuantResultSet, msiEm) 
    }
    
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRsm, quantRsm)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()
    
    // --- TODO: END OF merge following code with AbstractLabelFreeFeatureQuantifier ---

    ()
  }
  
  protected def computeAndStoreQuantEntities(msiQuantRsm: MsiResultSummary, quantRsm : ResultSummary) {
    
    // Quantify peptide ions if a label free quant config is provided
    val lfqConfigOpt = quantConfig.labelFreeQuantConfig
    
    val entitiesSummarizer = if( lfqConfigOpt.isEmpty ) {
      require(
        entityCache.identResultSummaries.length == 1,
        "can't mix isobaric tagging results from multiple result summaries without a label free config"
      )
      
      // Quantify reporter ions
      val mqReporterIons = _quantifyIdentRsm(entityCache.identResultSummaries.head).toArray
      
      new IsobaricTaggingEntitiesSummarizer(mqReporterIons)
    }
    else {
      
      // Retrieve label-free config
      val lfqConfig = lfqConfigOpt.get
      
      // Quantify reporter ions
      val mqReporterIonsByIdentRsmId = new LongMap[Array[MasterQuantReporterIon]]
      for(identRsm <- entityCache.identResultSummaries) {
        mqReporterIonsByIdentRsmId += identRsm.id -> _quantifyIdentRsm(identRsm).toArray
      }
      
      // Extract features from mzDB files
      val (pepByRunAndScanNbr, psmByRunAndScanNbr) = entityCache.getPepAndPsmByRunIdAndScanNumber(this.mergedResultSummary)
      val mapSetExtractor = new ExtractMapSet(
        executionContext.getLCMSDbConnectionContext,
        udsMasterQuantChannel.getName,
        entityCache.getLcMsRuns(),
        lfqConfig,
        Some(pepByRunAndScanNbr),
        Some(psmByRunAndScanNbr)
      )
      mapSetExtractor.run()
      
      // Retrieve some values
      val lcmsMapSet = mapSetExtractor.extractedMapSet
      val rawMapIds = lcmsMapSet.getRawMapIds()
      val lcMsScans = entityCache.getLcMsScans(rawMapIds)
      val spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber
      val ms2ScanNumbersByFtId = entityCache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)
      
      new IsobaricTaggingWithLabelFreeEntitiesSummarizer(
        mqReporterIonsByIdentRsmId,
        lcmsMapSet,
        spectrumIdByRsIdAndScanNumber,
        ms2ScanNumbersByFtId
      )
      
    }

    val(mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      this.masterQc,
      quantRsm,
      this.entityCache.identResultSummaries
    )
    
    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRsm,mqPeptides,mqProtSets)
  }
  
  protected lazy val quantPeptidesObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.ISOBARIC_TAGGING_QUANT_PEPTIDES.toString())
  }

  protected lazy val quantPeptideIonsObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.ISOBARIC_TAGGING_QUANT_PEPTIDE_IONS.toString())
  }
  
  protected def getMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    val mergedIdentRsmIdOpt = masterQc.identResultSummaryId
    require(mergedIdentRsmIdOpt.isDefined, "mergedIdentRsmIdOpt is not defined")
    
    if (mergedIdentRsmIdOpt.isEmpty) {
      isMergedRsmProvided = false
      createMergedResultSummary(msiDbCtx)
    } else {
      isMergedRsmProvided = true
      val identRsmId = mergedIdentRsmIdOpt.get
      this.logger.debug("Read Merged RSM with ID " + identRsmId)

      // Instantiate a RSM provider
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = null)
      val identRsm = rsmProvider.getResultSummary(identRsmId,true).get

      // FIXME: it should not be stored here but directly into the MasterQuantChannel table
      val mqcProperties = new MasterQuantChannelProperties(
        identResultSummaryId = Some(identRsmId)
      )
      udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqcProperties))
      
      identRsm
    }
  }
  
  // DBO: why is this needed ? 
  def getResultAsJSON(): String = {
    throw new Exception("Not Yet Implemented")
  }
  
  private def _quantifyIdentRsm(identRsm: ResultSummary): ArrayBuffer[MasterQuantReporterIon] = {
    
    val peaklistIdByIdentRsId = entityCache.peaklistIdByIdentRsId
    
    require(identRsm.resultSet.isDefined,"Quantified ResultSummary has no associated Result Set.") 
    val peaklistId = peaklistIdByIdentRsId(identRsm.getResultSetId())
    val identRsmQuantChannels = quantChannelsByIdentRsmId(identRsm.id)
    val identRsmQuantChannelsCount = identRsmQuantChannels.length
    
    val identifiedMs2QueryBySpectrumId = identRsm.resultSet.get.peptideMatches.toLongMapWith { pepMatch =>
      val ms2Query = pepMatch.msQuery.asInstanceOf[Ms2Query]
      (ms2Query.spectrumId,ms2Query)
    }
    
    val tagInfoTuples = identRsmQuantChannels.map { qc =>
      val tag = tagByQcId(qc.id)
      (qc.id, tag, msnMozTolInDaByTagId(tag.id))
    }
    //println("tagInfoTuples " + tagInfoTuples.toList)
    
    val masterQuantReporterIons = new ArrayBuffer[MasterQuantReporterIon](identifiedMs2QueryBySpectrumId.size)
    
    spectrumProvider.foreachPeaklistSpectrum(peaklistId) { spectrum =>
      
      val identifiedMs2QueryOpt = identifiedMs2QueryBySpectrumId.get(spectrum.id)
      
      // Quantify only identified PSMs
      if( identifiedMs2QueryOpt.isDefined ) {
        
        // Keep only peaks having a low m/z value
        val mozListToUse = spectrum.mozList.get.takeWhile( _ <= maxMassToUse )
        
        // Compute [moz,intensity] pairs
        val mzIntPairs = mozListToUse.zip(spectrum.intensityList.get.take(mozListToUse.length) )
        
        val quantReporterIonMap = new LongMap[QuantReporterIon](identRsmQuantChannelsCount)
        
        for( (qcId,tag,mozTolInDa) <- tagInfoTuples ) {
          val reporterMz = tag.reporterMz
          
          val peaksInRange = mzIntPairs.filter(p => (p._1 > reporterMz - mozTolInDa) && (p._1 < reporterMz + mozTolInDa) )
          
          if( peaksInRange.nonEmpty ) {
            val nearestPeak = peaksInRange.minBy(p => math.abs(reporterMz - p._1))
            
            val quantReporterIon = QuantReporterIon(
              quantChannelId = qcId,
              moz = nearestPeak._1,
              rawAbundance = nearestPeak._2,
              abundance = nearestPeak._2,
              selectionLevel = 2
            )
            
            quantReporterIonMap += Tuple2(qcId, quantReporterIon)
          }
        }
        
        val identifiedMs2Query = identifiedMs2QueryOpt.get
        
        masterQuantReporterIons += MasterQuantReporterIon(
          id = MasterQuantReporterIon.generateNewId(),
          charge = identifiedMs2Query.charge,
          elutionTime = spectrum.firstTime,
          msQueryId = identifiedMs2Query.id,
          spectrumId = spectrum.id,
          scanNumber = spectrum.firstScan,
          quantReporterIonMap = quantReporterIonMap.toMap,
          selectionLevel = 2
        )
      }
    }
    
    masterQuantReporterIons
  }
  
}