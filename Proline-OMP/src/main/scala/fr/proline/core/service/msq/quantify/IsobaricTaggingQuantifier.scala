package fr.proline.core.service.msq.quantify

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.ms._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context._
import fr.proline.core.algo.msq.config._
import fr.proline.core.algo.msq.summarizing._
import fr.proline.core.om.model.msi.LazyResultSummary
import fr.proline.core.om.model.msi.Ms2Query
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLSpectrumProvider
import fr.proline.core.orm.msi.{ ObjectTree => MsiObjectTree }
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import fr.proline.core.om.storer.msi.impl.RsmDuplicator

class IsobaricTaggingQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantMethod: IsobaricTaggingQuantMethod,
  val quantConfig: IsobaricTaggingQuantConfig
) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  private val groupSetupNumber = 1
  private val masterQcExpDesign = experimentalDesign.getMasterQuantChannelExpDesign(udsMasterQuantChannel.getNumber, groupSetupNumber)
  
  private val masterQc = experimentalDesign.masterQuantChannels.find(_.number == udsMasterQuantChannel.getNumber).get
  private val quantChannelsByIdentRsmId = masterQc.quantChannels.groupBy( _.identResultSummaryId )
  private val tagById = quantMethod.tagById
  private val tagByQcId = masterQc.quantChannels.toLongMapWith { qc =>
    (qc.id, tagById(qc.quantLabelId.get))
  }
  private val msnMozTolUnit = MassTolUnit.string2unit( quantConfig.extractionParams.mozTolUnit )
  private val msnMozTolInDaByTagId = quantMethod.quantLabels.toLongMapWith { tag =>
    (tag.id, calcMozTolInDalton(tag.reporterMz, quantConfig.extractionParams.mozTol, msnMozTolUnit) )
  }
  private val maxMassToUse = quantMethod.quantLabels.map(_.reporterMz).max + 1
  
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
    // get cloned and stored master quant result summary
    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)
    val rsmDuplicator =  new RsmDuplicator(rsmProvider)  
    val quantRSM = if(!isMergedRsmProvided) {        
    	rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRsm, msiQuantResultSet, true, msiEm) 
    } else {       
      rsmDuplicator.cloneAndStoreRSM(this.mergedResultSummary, msiQuantRsm, msiQuantResultSet, false, msiEm) 
    }
    
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRsm, quantRSM)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()
    
    // --- TODO: END OF merge following code with AbstractLabelFreeFeatureQuantifier ---

    ()
  }
  

  
  protected def computeAndStoreQuantEntities(msiQuantRsm: MsiResultSummary, quantRsm : ResultSummary) {
    
    logger.info("computing isobaric quant entities...")
    
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
      
      logger.info("computing label-free quant entities...")
      
      // Extract features from mzDB files
      val (pepByRunAndScanNbr, psmByRunAndScanNbr) = entityCache.getPepAndPsmByRunIdAndScanNumber(this.mergedResultSummary)
      val mapSetExtractor = new ExtractMapSet(
        executionContext.getLCMSDbConnectionContext,
        udsMasterQuantChannel.getName,
        entityCache.getLcMsRuns(),
        masterQcExpDesign,
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
    
    logger.info("summarizing quant entities...")

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
  
  protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
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
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = udsDbCtx)
      val identRsm = rsmProvider.getResultSummary(identRsmId,true).get

      // FIXME: it should not be stored here but directly into the MasterQuantChannel table
      val mqcProperties = new MasterQuantChannelProperties(
        identResultSummaryId = Some(identRsmId)
      )
      udsMasterQuantChannel.setSerializedProperties(ProfiJson.serialize(mqcProperties))
      
      identRsm
    }
  }
  
  
  private def _quantifyIdentRsm(identRsm: ResultSummary): ArrayBuffer[MasterQuantReporterIon] = {
    
    val peaklistIdByIdentRsId = entityCache.peaklistIdByIdentRsId
    
    require(identRsm.resultSet.isDefined,"Quantified ResultSummary has no associated Result Set.") 
    val peaklistId = peaklistIdByIdentRsId(identRsm.getResultSetId())
    val identRsmQuantChannels = quantChannelsByIdentRsmId(identRsm.id)
    val identRsmQuantChannelsCount = identRsmQuantChannels.length
    
    val identMs2QueryBySpecId = identRsm.resultSet.get.peptideMatches.toLongMapWith { pepMatch =>
      val ms2Query = pepMatch.msQuery.asInstanceOf[Ms2Query]
      (ms2Query.spectrumId,ms2Query)
    }
    
    val tagInfoTuples = identRsmQuantChannels.map { qc =>
      val tag = tagByQcId(qc.id)
      (qc.id, tag, msnMozTolInDaByTagId(tag.id))
    }
    //println("tagInfoTuples " + tagInfoTuples.toList)
    
    val masterQuantReporterIons = new ArrayBuffer[MasterQuantReporterIon](identMs2QueryBySpecId.size)

    _foreachIdentifiedSpectrum(peaklistId, identMs2QueryBySpecId) { case (specId, spectrum, identMs2Query) =>
      
      // Keep only peaks having a low m/z value
      val mozListToUse = spectrum.getMozList.takeWhile( _ <= maxMassToUse )
      
      // Compute [moz,intensity] pairs
      val mzIntPairs = mozListToUse.zip(spectrum.getIntensityList.take(mozListToUse.length) )
      
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
      
      masterQuantReporterIons += MasterQuantReporterIon(
        id = MasterQuantReporterIon.generateNewId(),
        charge = identMs2Query.charge,
        elutionTime = spectrum.getElutionTime,
        msQueryId = identMs2Query.id,
        spectrumId = specId,
        scanNumber = spectrum.getScanNumber,
        quantReporterIonMap = quantReporterIonMap,
        selectionLevel = 2
      )
    }
    
    masterQuantReporterIons
  }
  
  private def _foreachIdentifiedSpectrum(
    peaklistId: Long,
    identifiedMs2QueryBySpectrumId: LongMap[Ms2Query]
  )( onEachSpectrum: (Long, IsobaricTaggingQuantifier.ISpectrum, Ms2Query) => Unit ): Unit = {
    
    import fr.profi.mzdb.db.model.params.param.CVEntry
    import fr.profi.mzdb.model.SpectrumHeader
    
    val reporterIonDataSource = quantConfig.reporterIonDataSource
    val isProlineDataSource = (reporterIonDataSource == ReporterIonDataSource.PROLINE_SPECTRUM)
    
    val mzDbReaderOpt = if(isProlineDataSource) None
    else {
      val rawFileOpt = entityCache.rawFileByPeaklistId.get(peaklistId)
      assert( rawFileOpt.isDefined, s"can't find a raw file corresponding to peaklist #$peaklistId" )
      
      val mzDbFilePathOpt = rawFileOpt.get.getMzdbFilePath()
      assert( mzDbFilePathOpt.isDefined, s"mzDB file is not linked to the raw file '${rawFileOpt.get.name}' in the UDSdb")
      
      Some(new fr.profi.mzdb.MzDbReader(mzDbFilePathOpt.get, true) )
    }
    
    lazy val ms3SpectraHeaderByMs2SpecId = if (mzDbReaderOpt.isEmpty) LongMap.empty[SpectrumHeader]
    else {
      val mzDbReader = mzDbReaderOpt.get
      
      // Enable meta-data loading
      mzDbReader.enableScanListLoading()
      mzDbReader.enablePrecursorListLoading()
      
      val spectraHeaders = mzDbReaderOpt.get.getSpectrumHeaders
      val spectraHeadersByCycle = spectraHeaders.groupBy(_.getCycle)
      val ms3ShMap = new LongMap[SpectrumHeader](spectraHeaders.length)
      
      for( (cycle, msnSpecHeadersInCycle) <- spectraHeadersByCycle ) {
        
        val ms2SpecHeadersInCycle = new ArrayBuffer[SpectrumHeader](msnSpecHeadersInCycle.length)
        val ms3SpecHeadersInCycle = new ArrayBuffer[SpectrumHeader](msnSpecHeadersInCycle.length)
        
        for (sh <- msnSpecHeadersInCycle) {
          if (sh.getMsLevel == 2) {
            ms2SpecHeadersInCycle += sh
          }
          else if (sh.getMsLevel == 3) {
            ms3SpecHeadersInCycle += sh
          }
        }
        
        for (ms3SpecHeader <- ms3SpecHeadersInCycle) {
          
          val ms3SpecInitialId = ms3SpecHeader.getInitialId
          //println( ms3SpecHeader.getElutionTime )
          //println( ms3SpecInitialId )
          //println( ms3SpecHeader.getMsLevel )
          
          val thermoMetaData = ms3SpecHeader.getScanList().getScans().get(0).getThermoMetaData()
          assert(thermoMetaData != null, "Isobaric quantification of MS3 data is only supported for Thermo data")
          
          val ms1Target = thermoMetaData.getTargets().apply(0)
          val ms1TargetMz = ms1Target.getMz
          //println( "ms1TargetMz", ms1TargetMz )
          
          val( nearestMs2Sh, lowestDiff ) = ms2SpecHeadersInCycle.map { ms2Sh =>
            val isolationWindow = ms2Sh.getPrecursor.getIsolationWindow
            val isolationWindowTargetMz = isolationWindow.getCVParam(CVEntry.ISOLATION_WINDOW_TARGET_MZ).getValue.toFloat
            ms2Sh -> math.abs(isolationWindowTargetMz - ms1TargetMz)
          }.minBy { _._2 }

          //println( "nearestMs2Sh.getPrecursorMz", nearestMs2Sh.getPrecursorMz )
          
          // Check m/z difference between nearestMs2Sh precursor m/z and ms2TargetMz is not too high
          assert(
            lowestDiff < 0.05,
            s"Can't find an MS2 spectrum corresponding to the MS3 spectrum #$ms3SpecInitialId"
          )
          
          val ms2SpecInitialId = nearestMs2Sh.getInitialId
          assert(
            ms3ShMap.contains(ms2SpecInitialId) == false,
            s"The MS2 spectrum #$ms2SpecInitialId is already mapped to another MS3 spectrum (#${ms3ShMap(ms2SpecInitialId).getId}), "+
            s"can't create a new mapping with MS3 spectrum #$ms3SpecInitialId"
          )
          
          ms3ShMap.put(ms2SpecInitialId, ms3SpecHeader)
        }
        
      } // ends foreach spectraHeadersByCycle
      
      ms3ShMap
    }
    
    val mzDbFileName = mzDbReaderOpt.map( mzdb => new java.io.File(mzdb.getDbLocation).getName ).getOrElse("")
    
    try {
      val spectrumProvider = new SQLSpectrumProvider(msiDbCtx)
      
      // Iterate over the spectra
      spectrumProvider.foreachPeaklistSpectrum(peaklistId, loadPeaks = isProlineDataSource) { spectrum =>
        
        val identifiedMs2QueryOpt = identifiedMs2QueryBySpectrumId.get(spectrum.id)
        
        // Quantify only identified PSMs
        if (identifiedMs2QueryOpt.isDefined) {
          val specId = spectrum.id
          val identifiedMs2Query = identifiedMs2QueryOpt.get
          
          reporterIonDataSource match {
            case ReporterIonDataSource.PROLINE_SPECTRUM => onEachSpectrum(specId, spectrum, identifiedMs2Query)
            case ReporterIonDataSource.MZDB_MS2_SPECTRUM => {
              require(
                spectrum.firstScan > 0,
                s"mzDB file $mzDbFileName: undefined firstScan for Proline spectrum #$specId, you may have provided a wrong peaklist software"
              )
              
              val mzDbSpectrum = mzDbReaderOpt.get.getSpectrum(spectrum.firstScan)
              onEachSpectrum(specId, mzDbSpectrum, identifiedMs2Query)
            }
            case ReporterIonDataSource.MZDB_MS3_SPECTRUM => {
              require(
                spectrum.firstScan > 0,
                s"mzDB file $mzDbFileName: undefined firstScan for Proline spectrum #$specId, you may have provided a wrong peaklist software"
              )
              
              val mzDbReader =  mzDbReaderOpt.get
              val ms3ShOpt = ms3SpectraHeaderByMs2SpecId.get(spectrum.firstScan)
              //assert( ms3ShsOpt.isDefined, s"can't find MS3 spectra in mzDB file for Proline spectrum #$specId" )
              
              if (ms3ShOpt.isDefined) {
                onEachSpectrum(specId, mzDbReader.getSpectrum(ms3ShOpt.get.getId), identifiedMs2Query)
              } else {
                logger.warn(s"can't find an MS3 spectrum in the mzDB file $mzDbFileName for Proline MS2 spectrum #$specId")
              }
              
            } // ends case ReporterIonDataSource.MZDB_MS3_SPECTRUM
          }
        } // ends if (identifiedMs2QueryOpt.isDefined)
      } // ends foreachPeaklistSpectrum
      
    } finally {
      if (mzDbReaderOpt.isDefined) {
        mzDbReaderOpt.get.close()
      }
    }
    
    ()
  }
  
}

object IsobaricTaggingQuantifier {
  
  sealed trait ISpectrum extends Any {
    def getScanNumber(): Int
    def getElutionTime(): Float
    def getMozList(): Array[Double]
    def getIntensityList(): Array[Float]
  }
  
  implicit class ProlineSpectrumWrapper(val spectrum: Spectrum) extends AnyVal with ISpectrum {
    def getScanNumber(): Int = spectrum.firstScan
    def getElutionTime(): Float = spectrum.firstTime
    def getMozList(): Array[Double] = spectrum.mozList.get
    def getIntensityList(): Array[Float] = spectrum.intensityList.get
  }
  
  implicit class MzDbSpectrumWrapper(val spectrum: fr.profi.mzdb.model.Spectrum) extends AnyVal with ISpectrum {
    def getScanNumber(): Int = spectrum.getHeader.getInitialId
    def getElutionTime(): Float = spectrum.getHeader.getElutionTime
    def getMozList(): Array[Double] = spectrum.getData.getMzList
    def getIntensityList(): Array[Float] = spectrum.getData.getIntensityList
  }
}
