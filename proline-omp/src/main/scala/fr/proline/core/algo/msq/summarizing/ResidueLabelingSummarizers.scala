package fr.proline.core.algo.msq.summarizing

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.core.algo.lcms.MqPepIonAbundanceSummarizingMethod
import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, LongMap}

/**
 * @author Christophe Bruley
 *
 */

class ResidueLabelingEntitiesSummarizer(
  qcByRSMIdAndTagId: Map[(Long, Long), QuantChannel],
  tagByPtmId: LongMap[Long],
  lcmsMapSet: MapSet,
  spectrumIdByRsIdAndScanNumber: LongMap[LongMap[Long]],
  ms2ScanNumbersByFtId: LongMap[Array[Int]],
  abundanceSummarizerMethod: MqPepIonAbundanceSummarizingMethod.Value
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {

  def this(qcByRSMIdAndTagId: Map[(Long, Long), QuantChannel],
            tagByPtmId: LongMap[Long],
            lcmsMapSet: MapSet,
            spectrumIdByRsIdAndScanNumber: LongMap[LongMap[Long]],
            ms2ScanNumbersByFtId: LongMap[Array[Int]] ){
      this(qcByRSMIdAndTagId, tagByPtmId, lcmsMapSet, spectrumIdByRsIdAndScanNumber, ms2ScanNumbersByFtId,  MqPepIonAbundanceSummarizingMethod.SUM)
  }
  
  //type CombinedQIons = (QuantPeptideIon,MasterQuantPeptideIon,Long)
  private val childMapsCount = lcmsMapSet.childMaps.length
  
  type LFQPepIon = (QuantPeptideIon,Long, MasterQuantPeptide,Int, Boolean)
  
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
    
    val identRsmIdByRunId = quantChannels.map { qc =>
      qc.runId.get -> qc.identResultSummaryId
    }.toMap
    
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
      abundanceSummarizerMethod).computeMasterQuantPeptides(
      labelFreeMasterQuantChannelFake,
      quantMergedRSM,
      resultSummaries
    )

    //VDS : lfMqPeptides mqPeptide for peptide with an ab value.
    val lfQPepIonsByKey = new mutable.HashMap[String, ArrayBuffer[LFQPepIon]]()

    val (fakeMQPep, nonFakeMQPep) = lfMqPeptides.partition(mqP => mqP.peptideInstance.get.properties.isDefined && mqP.peptideInstance.get.properties.get.getComment.isDefined)
    logger.info(" NB fake = "+fakeMQPep.length+" NB not Fake "+nonFakeMQPep.length)
    for(mqPep <- lfMqPeptides;
        mqPepIon <- mqPep.masterQuantPeptideIons;
        (fakeQcId,qPepIon) <- mqPepIon.quantPeptideIonMap) {
      val identRsmId = identRsmIdByFakeQcId(fakeQcId)
      val peptide = mqPep.peptideInstance.get.peptide
      val key = peptide.sequence + peptide.ptms.filter(p => !tagByPtmId.contains(p.definition.id)).map(_.toPtmString()).mkString("%")
      val isFake = mqPep.peptideInstance.get.properties.isDefined &&  mqPep.peptideInstance.get.properties.get.getComment.isDefined
      val mQPepIons = lfQPepIonsByKey.getOrElseUpdate(key, new ArrayBuffer[LFQPepIon]())
      mQPepIons += Tuple5(qPepIon, identRsmId, mqPep, mqPepIon.charge,isFake)
    }

    val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]( quantMergedRSM.peptideInstances.length )

    for ((key, ions) <- lfQPepIonsByKey) {

      var lfMqPep: MasterQuantPeptide = {
        //Consider only non fake "ion"/peptide. If all are fake (should not occur), consider all ions/peptides
        val validIons= if(ions.count(!_._5)>0) ions.filter(x => !x._5) else ions
        // search for the best MasterQuantPeptide to use, regardless the ion charge
        // for each MQPep calculate the number of ptm matching a tag and select the mqp with the smallest number of
        // PTM tag (0 means no PTM corresponding to a tag ... generally ot correspond to the Light)
        //VDS : list.collect(map) create List from call to "map.get(list.value)"  => result will be 'nb Tag -> mqPep'
        val mQPepAndTags = validIons.map(_._3).distinct.map(mqp => (mqp.peptideInstance.get.peptide.ptms.map(_.definition.id).collect(tagByPtmId).distinct.length, mqp))
        mQPepAndTags.minBy(_._1)._2
      }

      val combinedMqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()

      for ((charge, lfQIons) <- ions.groupBy(_._4)) {
        if(lfQIons.length > 1)
          logger.debug(s"combining ${lfQIons.length} ions for key $key, ${charge}+")
        val quantPepIonsBuffer = new ArrayBuffer[QuantPeptideIon](qcCount)

        var lfqMQPepIon: MasterQuantPeptideIon = {
          // determine again the best MasterQuantPeptide to select the best MQPeptideIon since the current charge state could be missing in lfMqPep
          // at this step, best MasterQuantPeptide is search only within the set of MQPep matching the charge state.
          val mQPepAndTags = lfQIons.map(_._3).distinct.map(mqp => (mqp.peptideInstance.get.peptide.ptms.map(_.definition.id).collect(tagByPtmId).distinct.length, mqp))
          // select the MQPep matching the smallest number of tag (0 means "unmodified" regarding the quantitation method)
          mQPepAndTags.sortBy(_._1).head._2.masterQuantPeptideIons.filter(_.charge == charge).head
        }

        for ((qPepIon, rsmId, mqPep, charge, isFake) <- lfQIons) {
          val ptms = mqPep.peptideInstance.get.peptide.ptms.map(_.definition.id)
          val matchingTags = ptms.collect(tagByPtmId).distinct //for (ptmId <- ptms) yield tagByPtmId(ptmId)
          if (matchingTags.isEmpty) {
            val qc = qcByRSMIdAndTagId((rsmId, tagByPtmId(-1L)))
            quantPepIonsBuffer += qPepIon.copy(
              quantChannelId = qc.id
            )
          } else if (matchingTags.length == 1) {
            val qc = qcByRSMIdAndTagId((rsmId, matchingTags.head))
            quantPepIonsBuffer += qPepIon.copy(
              quantChannelId = qc.id
            )
          } else {
            logger.warn(s"QPepIon ($key,$charge) match for more than one quantitation labels")
          }
        }

        combinedMqPepIons += lfqMQPepIon.copy(
          id = MasterQuantPeptideIon.generateNewId(),
          quantPeptideIonMap = quantPepIonsBuffer.mapByLong(_.quantChannelId)
        )

      }

      masterQuantPeptides += BuildMasterQuantPeptide(
        combinedMqPepIons,
        lfMqPep.peptideInstance,
        quantMergedRsmId,
        abundanceSummarizerMethod
      )

    }

    logger.info("Found "+masterQuantPeptides.length+" MQPeptides ")
    masterQuantPeptides.toArray
  }
}