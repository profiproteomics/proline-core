package fr.proline.core.algo.msq.summarizing

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.core.algo.lcms.IonAbundanceSummarizerMethod
import fr.proline.core.algo.msq.config.profilizer.AbundanceSummarizerMethod
import fr.proline.core.algo.msq.profilizer.AbundanceSummarizer
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

import scala.collection.mutable.{ArrayBuffer, LongMap}

/**
 * @author David Bouyssie
 *
 */
class IsobaricTaggingEntitiesSummarizer(
  mqReporterIons: Array[MasterQuantReporterIon]
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {
  
  def computeMasterQuantPeptides(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    
    require(
      resultSummaries.length == 1,
      "can't mix isobaric tagging results from multiple result summaries"
    )
    
    // Define some values
    val mergedRsmId = quantMergedRSM.id
    val quantChannels = masterQuantChannel.quantChannels
    val qcIds = quantChannels.map(_.id)
    
    // Define some mappings
    val masterPepInstByPepId = quantMergedRSM.peptideInstances.toLongMapWith( pi => pi.peptide.id -> pi )
    val mqReportersCount = mqReporterIons.length
    val mqReporterIonByMsQueryId = mqReporterIons.mapByLong(_.msQueryId)
    /*if( qPeptideIonsByIdentRsmId.isEmpty ) {
      val allMqReporterIons = mqReporterIonsByIdentRsmId.values.flatten
      allMqReporterIons.groupBy(_.scanNumber)
    }*/
    
    val mqPepIonsByPeptideId = new LongMap[Array[MasterQuantPeptideIon]](resultSummaries.head.peptideInstances.length)
    
    // Iterate over validated peptide instances
    for(
      rsm <- resultSummaries;
      pepInst <- rsm.peptideInstances;
      // TODO: check why sometimes masterPepInst is not defined
      masterPepInst <- masterPepInstByPepId.get(pepInst.peptideId)
    ) {
      val pepMatches = pepInst.peptideMatches
      val pepMatchesGroupedByCharge = pepMatches.groupBy(_.charge)
      
      val pepInstIdOpt = Some(pepInst.id)
      val peptideIdOpt = Some(pepInst.peptideId)
      //val masterPepInstOpt = masterPepInstByPepId.get(pepInst.peptideId)
      val masterPepInstIdOpt = Some(masterPepInst.id)
      
      val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon](pepMatchesGroupedByCharge.size)
      for( (charge,sameChargePepMatches) <- pepMatchesGroupedByCharge ) {
        
        val msQueryIds = sameChargePepMatches.map(_.msQueryId).toArray
        val mqReporterIons = msQueryIds.flatMap( mqReporterIonByMsQueryId.get(_) )
        
        if( mqReporterIons.nonEmpty) {
        
          // Compute the matrix of raw abundances for the MQ reporter ions corresponding to this charge state
          val mqReporterIonRawAbundanceMatrix = mqReporterIons.map { mqReporterIon =>
            require(
              mqReporterIon.charge == charge,
              "invalid master quant reporter ion charge for ms query with id="+ mqReporterIon.msQueryId
            )
            
            val qRepIonMap = mqReporterIon.quantReporterIonMap
            qcIds.map( qRepIonMap.get(_).map(_.rawAbundance).getOrElse(Float.NaN) )
          }
          
          // Take minimum elution time as a reference for all quant peptide ions
          val firstMqReporterIon = mqReporterIons.minBy(_.scanNumber)
          val firstElutionTime = firstMqReporterIon.elutionTime
          val firstScanNumber = firstMqReporterIon.scanNumber
          
          // Summarize abundance matrix
          val summarizedRawAbundanceMatrix = AbundanceSummarizer.summarizeAbundanceMatrix(
            mqReporterIonRawAbundanceMatrix,
            AbundanceSummarizerMethod.MEDIAN_PROFILE
          )
          
          // Retrieve the best peptide match
          val bestPepMatch = sameChargePepMatches.maxBy(_.score)
          val moz = bestPepMatch.msQuery.moz
          val pepMatchesCount = sameChargePepMatches.length
          
          val qPepIonByQcId = qcIds.zip(summarizedRawAbundanceMatrix).toLongMapWith { case (qcId,rawAbundance) =>
            qcId -> new QuantPeptideIon(
              rawAbundance = rawAbundance,
              abundance = rawAbundance,
              moz = moz,
              elutionTime = firstElutionTime,
              duration = 0,
              correctedElutionTime = firstElutionTime,
              scanNumber = firstScanNumber,
              peptideMatchesCount = pepMatchesCount,
              ms2MatchingFrequency = None,
              bestPeptideMatchScore = Some(bestPepMatch.score),
              predictedElutionTime = None,
              quantChannelId = qcId,
              peptideId = peptideIdOpt,
              peptideInstanceId = pepInstIdOpt,
              msQueryIds = Some(msQueryIds),
              lcmsFeatureId = None,
              lcmsMasterFeatureId = None,
              unmodifiedPeptideIonId = None, // TODO: set this value ???
              selectionLevel = 2
            )
          }
          
          mqPepIons += new MasterQuantPeptideIon(
            id = MasterQuantPeptideIon.generateNewId(),
            unlabeledMoz = moz,
            charge = charge,
            elutionTime = firstElutionTime,
            peptideMatchesCount = pepMatchesCount,
            calculatedMoz = None,
            masterQuantPeptideId = 0,
            resultSummaryId = mergedRsmId,
            peptideInstanceId = masterPepInstIdOpt,
            bestPeptideMatchId = Some(bestPepMatch.id),
            lcmsMasterFeatureId = None,
            unmodifiedPeptideIonId = None,
            selectionLevel = 2,
            quantPeptideIonMap = qPepIonByQcId,
            properties = None,
            masterQuantReporterIons = mqReporterIons
          )
        }
      }
      
      if( mqPepIons.nonEmpty ) {
        mqPepIonsByPeptideId += peptideIdOpt.get -> mqPepIons.toArray
      }
      
    }
    
    // --- Convert master quant peptide ions into master quant peptides ---
    val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]( quantMergedRSM.peptideInstances.length )
    
    for( masterPepInst <- quantMergedRSM.peptideInstances ) {
      val mqPepIonsOpt = mqPepIonsByPeptideId.get(masterPepInst.peptideId)
      if (mqPepIonsOpt.isDefined) {
        masterQuantPeptides += BuildMasterQuantPeptide(mqPepIonsOpt.get, Some(masterPepInst), mergedRsmId)
      }
    }

    masterQuantPeptides.toArray
  }
  
  /*
  private def _buildQuantPeptideIon() {
    
  }
  
  private def _buildMasterQuantPeptideIon(
    sameChargePepMatches: Seq[PeptideMatch],
    mqReporterIons: Array[MasterQuantReporterIon]
  ) {
    
  }
*/
 
}

class IsobaricTaggingWithLabelFreeEntitiesSummarizer(
  mqReporterIonsByIdentRsmId: LongMap[Array[MasterQuantReporterIon]],
  lcmsMapSet: MapSet,
  spectrumIdByRsIdAndScanNumber: LongMap[LongMap[Long]],
  ms2ScanNumbersByFtId: LongMap[Array[Int]],
  abundanceSummarizerMethod: IonAbundanceSummarizerMethod.Value
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {

  def this( mqReporterIonsByIdentRsmId: LongMap[Array[MasterQuantReporterIon]],
            lcmsMapSet: MapSet,
            spectrumIdByRsIdAndScanNumber: LongMap[LongMap[Long]],
            ms2ScanNumbersByFtId: LongMap[Array[Int]]){
    this(mqReporterIonsByIdentRsmId, lcmsMapSet,spectrumIdByRsIdAndScanNumber, ms2ScanNumbersByFtId, IonAbundanceSummarizerMethod.BEST_ION)
  }


  //type CombinedQIons = (QuantPeptideIon,MasterQuantPeptideIon,Long)
  private val childMapsCount = lcmsMapSet.childMaps.length
  
  type LFQPepIon = (QuantPeptideIon,MasterQuantPeptide,Int)
  
  def computeMasterQuantPeptides(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    
    require( resultSummaries.length == childMapsCount, "invalid number of result summaries")
    
    // Define some values
    val quantMergedRsmId = quantMergedRSM.id
    val quantChannels = masterQuantChannel.quantChannels
    val qcCount = quantChannels.length
    
    // Define some mappings
    val qcIdsByIdentRsmId = quantChannels.groupBy( _.identResultSummaryId ).map { case (identRsmId,qcs) =>
      identRsmId -> qcs.map(_.id)
    }
    val identRsmIdByRunId = quantChannels.map { qc =>
      qc.runId.get -> qc.identResultSummaryId
    } toMap
    
    // Build fake quant channels to perform label-free entities summarizing
    val identRsmIdByFakeQcId = new LongMap[Long](childMapsCount)
    val lcMsQuantChannelFakes = lcmsMapSet.childMaps.map { childMap =>
      
      val runId = childMap.runId.get
      val identRsmId = identRsmIdByRunId(runId)
      
      val qcId = QuantChannel.generateNewId()
      identRsmIdByFakeQcId += qcId -> identRsmId
      
      QuantChannel(
        id = qcId,
        number = childMap.number,
        name = masterQuantChannel.name.getOrElse("TMP quant channel"),
        sampleNumber = 0,
        identResultSummaryId = identRsmId,
        lcmsMapId = Some(childMap.id),
        runId = Some(runId)
      )
    }
    
    val labelFreeMasterQuantChannelFake = masterQuantChannel.copy(
      lcmsMapSetId = Some(lcmsMapSet.id),
      quantChannels = lcMsQuantChannelFakes
    )
    
    // Perform label-free entities summarizing
    val lfMqPeptides = new LabelFreeEntitiesSummarizer(
      lcmsMapSet = lcmsMapSet,
      spectrumIdByRsIdAndScanNumber = spectrumIdByRsIdAndScanNumber,
      ms2ScanNumbersByFtId = ms2ScanNumbersByFtId,
      abundanceSummarizerMethod
    ).computeMasterQuantPeptides(
      labelFreeMasterQuantChannelFake,
      quantMergedRSM,
      resultSummaries
    )
    
    // Convert master quant peptides into quant peptides ions mapped by their corresponding identification RSM id
    val lfQPepIonsByIdentRsmId = new LongMap[ArrayBuffer[LFQPepIon]](childMapsCount)
    val approxQPepIonsCount = lfMqPeptides.length * 2
    
    for(
      mqPep <- lfMqPeptides;
      mqPepIon <- mqPep.masterQuantPeptideIons;
      (qcId,qPepIon) <- mqPepIon.quantPeptideIonMap
    ) {
      val identRsmId = identRsmIdByFakeQcId(qcId)
      val qPepIons = lfQPepIonsByIdentRsmId.getOrElseUpdate(identRsmId, new ArrayBuffer[LFQPepIon](approxQPepIonsCount))
      qPepIons += Tuple3(qPepIon, mqPep, mqPepIon.charge)
    }
    
    val isoMqPepIonsByChargeByPepId = new LongMap[LongMap[ArrayBuffer[MasterQuantPeptideIon]]](lfMqPeptides.length)
    val combinedQPepIonsByChargeByPepId = new LongMap[LongMap[ArrayBuffer[QuantPeptideIon]]](lfMqPeptides.length)
    
    // Combine MQ reporter ions with label-free quant peptide ions
    for( (identRsmId,mqReporterIons) <- mqReporterIonsByIdentRsmId ) {
      
      val qcIds = qcIdsByIdentRsmId(identRsmId)
      
      // Retrieve label-free quant peptides for this child RSM
      val lfQPepIons = lfQPepIonsByIdentRsmId(identRsmId)
      logger.debug(lfQPepIons.length + " peptide ions have been quantified using the label-free method")
      
      val lfQPepIonByPepIdAndCharge = Map() ++ lfQPepIons.withFilter(_._2.peptideInstance.isDefined).map { case (qPepIon,mqPep,charge) =>
        (mqPep.peptideInstance.get.peptideId,charge) -> qPepIon
      }
      
      val rsmOpt = resultSummaries.find(_.id == identRsmId)
      assert( rsmOpt.isDefined, "can't find the identified result summary with id="+ identRsmId)
      
      // Summarize MQ peptides from MQ reporter ions quantified in this RSM
      val isobaricMqPeps = new IsobaricTaggingEntitiesSummarizer(
        mqReporterIons
      ).computeMasterQuantPeptides(masterQuantChannel, quantMergedRSM, Array(rsmOpt.get) )
      
      // Combine isobaric MQ peptides with label-free quant peptides
      for (isoMqPep <- isobaricMqPeps) {
        val peptideId = isoMqPep.peptideInstance.get.peptideId
        
        val isoMqPepIonsByCharge = isoMqPepIonsByChargeByPepId.getOrElseUpdate(
          peptideId,
          new LongMap[ArrayBuffer[MasterQuantPeptideIon]](isoMqPep.masterQuantPeptideIons.length)
        )
        val combinedQPepIonsByCharge = combinedQPepIonsByChargeByPepId.getOrElseUpdate(
          peptideId,
          new LongMap[ArrayBuffer[QuantPeptideIon]](isoMqPep.masterQuantPeptideIons.length)
        )
        
        for( isoMqPepIon <- isoMqPep.masterQuantPeptideIons ) {
          val charge = isoMqPepIon.charge
          val qPepIonOpt = lfQPepIonByPepIdAndCharge.get((peptideId,charge))
          
          if( qPepIonOpt.isEmpty ) {
            this.logger.warn(s"Can't mix LF and isobaric ions, no LF quantified ion with charge=$charge and peptide id=$peptideId")
          } else {
            
            // Map current isobaric master quant peptide by its charge state
            isoMqPepIonsByCharge.getOrElseUpdate(
              charge,
              new ArrayBuffer[MasterQuantPeptideIon](childMapsCount)
            ) += isoMqPepIon
            
            val qPepIon = qPepIonOpt.get
            val qPepIonRawAb = qPepIon.rawAbundance
            
            val isoMqPepIonRawAbundances = isoMqPepIon.getRawAbundancesForQuantChannels(qcIds)
            val isoMqPepIonRawAbundanceSum = isoMqPepIonRawAbundances.filterNot(_.isNaN).sum
            val scaledIsoMqPepIonRawAbundances = isoMqPepIonRawAbundances.map( _ * qPepIonRawAb / isoMqPepIonRawAbundanceSum )
            
            val combinedQPepIonsBuffer = combinedQPepIonsByCharge.getOrElseUpdate(
              charge,
              new ArrayBuffer[QuantPeptideIon](qcCount)
            )
            
            for( (qcId,scaledRawAbundance) <- qcIds.zip(scaledIsoMqPepIonRawAbundances) ) {
              combinedQPepIonsBuffer +=  qPepIon.copy(
                rawAbundance = scaledRawAbundance,
                abundance = scaledRawAbundance,
                quantChannelId = qcId
              )
            }
          }
        }
      }
    }
    
    // --- Combine master quant peptide ions to build master quant peptides ---
    val lfMqPepByPepId = lfMqPeptides.mapByLong( _.peptideInstance.get.peptideId )
    val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]( quantMergedRSM.peptideInstances.length )
    
    for(
      (pepId,combinedQPepIonsByCharge) <- combinedQPepIonsByChargeByPepId;
      if combinedQPepIonsByCharge.nonEmpty; // Exclude identified but not quantified peptides
      lfMqPep <- lfMqPepByPepId.get(pepId) // TODO: check why not always defined
    ) {
      
      val lfMqPepIonByCharge = lfMqPep.masterQuantPeptideIons.mapByLong(_.charge)
      val isoMqPepIonsByCharge = isoMqPepIonsByChargeByPepId(pepId)
      
      val combinedMqPepIons = for( (charge, combinedQPepIons) <- combinedQPepIonsByCharge) yield {
        val lfMqPepIon = lfMqPepIonByCharge(charge)
        val isoMqPepIons = isoMqPepIonsByCharge(charge)
        
        lfMqPepIon.copy(
          id = MasterQuantPeptideIon.generateNewId(),
          quantPeptideIonMap = combinedQPepIons.mapByLong(_.quantChannelId),
          masterQuantReporterIons = isoMqPepIons.flatMap(_.masterQuantReporterIons).toArray
        )
      }
      
      masterQuantPeptides += BuildMasterQuantPeptide(
        combinedMqPepIons.toSeq,
        lfMqPep.peptideInstance,
        quantMergedRsmId,
        abundanceSummarizerMethod
      )
      
    }
    
    masterQuantPeptides.toArray
  }
}