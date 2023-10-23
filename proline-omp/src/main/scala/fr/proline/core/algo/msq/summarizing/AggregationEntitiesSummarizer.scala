package fr.proline.core.algo.msq.summarizing

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.core.algo.msq.config.AbundanceComputationMethod
import fr.proline.core.om.model.msi.{PeptideInstance, ResultSummary}
import fr.proline.core.om.model.msq._

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ArrayBuffer

class AggregationEntitiesSummarizer(
  childQuantRSMbychildMQCId: Map[Long, QuantResultSummary],
  quantChannelsMapping: Map[Long, QuantChannel],
  intensityComputation: AbundanceComputationMethod.Value = AbundanceComputationMethod.INTENSITY_SUM,
  isIsobaricTaggingMethod : Boolean = false
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {


  private class MQPepsComputer(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRsm: ResultSummary
  ) extends LazyLogging {

    val masterPepInstByPepId = quantMergedRsm.peptideInstances.toLongMapWith(pi => pi.peptide.id -> pi)
    // Link each child masterQuantPeptide to it's own MasterQuantChannel Id
    val childMQPeptidesByChildMQCId: immutable.Iterable[(Long, MasterQuantPeptide)] = childQuantRSMbychildMQCId.map { case (mqcId, quantRSM) => quantRSM.masterQuantPeptides.map((mqcId, _)) }.flatten
    // ... then build a Map peptideIds -> Map[ ChildMasterQuantChannelId, ChildMasterQuantPeptide ]
    val childMQPeptidesByPeptideId: Map[Long, Map[Long, MasterQuantPeptide]] = childMQPeptidesByChildMQCId.groupBy(_._2.peptideInstance.get.peptideId).map { case (pepId, pairs) => (pepId, pairs.toMap) }

    def computeMasterQuantPeptides(): Array[MasterQuantPeptide] = {

      val newMasterQuantPeptides = new ArrayBuffer[MasterQuantPeptide](childMQPeptidesByPeptideId.size)

      if(isIsobaricTaggingMethod)
        logger.debug(" ---- AggregationEntitiesSummarizer for isobaric ! ")

      for ((peptideId, masterPepInst) <- masterPepInstByPepId) {

        val masterQuantPeptidesBymQChlOpt = childMQPeptidesByPeptideId.get(peptideId) // get Map[ ChildMasterQuantChannelId, ChildMasterQuantPeptide ]

        if (masterQuantPeptidesBymQChlOpt.isDefined) {

          val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()
          // Map[Charge --> Iterable[(ChildMasterQuantChannelId, MasterQuantPeptideIon)] ]
          val mqPepIonsByChargeAndMQC = masterQuantPeptidesBymQChlOpt.get.map{ case(k,v) => v.masterQuantPeptideIons.map( (k, _ ))}.flatten.groupBy(_._2.charge)
          for (charge <- mqPepIonsByChargeAndMQC.keys)  {
            val mqPepIonOpt = _summarize(mqPepIonsByChargeAndMQC(charge), quantChannelsMapping, masterPepInst)
            if (mqPepIonOpt.isDefined) {
              mqPepIons += mqPepIonOpt.get
            }
          }
          if (mqPepIons.nonEmpty) {
            newMasterQuantPeptides += BuildMasterQuantPeptide(mqPepIons, Some(masterPepInst), quantMergedRsm.id)
          }
        }
      }
      logger.debug(" ---- NBR newMasterQuantPeptides "+newMasterQuantPeptides.size)
      newMasterQuantPeptides.toArray
    }

    private def _summarize(
      mqPepIonsByMQC: Iterable[(Long, MasterQuantPeptideIon)],
      quantChannelsMapping: Map[Long, QuantChannel],
      masterPepInst: PeptideInstance): Option[MasterQuantPeptideIon] = {

//      if(isIsobaricTaggingMethod) {
//         _summarizeWithReporterIons(mqPepIonsByMQC, quantChannelsMapping, masterPepInst)
//      } else {
        val newQuantPepIons = new ArrayBuffer[QuantPeptideIon]()
        val childQCIdsByAggQC = quantChannelsMapping.toSeq.groupBy(_._2).mapValues(_.map(_._1).seq)
        val quantPepIonsUnion = mqPepIonsByMQC.flatMap(_._2.quantPeptideIonMap.values).toList
        val templateMQPepIon = mqPepIonsByMQC.map(_._2).maxBy(_.calcRawAbundanceSum())
        val newMQPepIonId = MasterQuantPeptideIon.generateNewId()

        //For isobaric aggregation
        val mqReporterIonsUnion =  mqPepIonsByMQC.map(_._2).flatMap(_.masterQuantReporterIons)
        val newMQuantRepIons = new ArrayBuffer[MasterQuantReporterIon]()
        mqReporterIonsUnion.foreach(mqRepIon =>{
          val newQRepMap = new mutable.LongMap[QuantReporterIon]()
          mqRepIon.quantReporterIonMap.foreach( entry=> {
            val qChId = quantChannelsMapping(entry._1).id
            newQRepMap.put(qChId ,entry._2.copy(quantChannelId =qChId ))
          })
          newMQuantRepIons += mqRepIon.copy(
            id=MasterQuantReporterIon.generateNewId(),
            quantReporterIonMap = newQRepMap,
            masterQuantPeptideIonId = Some(newMQPepIonId))
        })

        //For MQPep properties info
        val mqPepIonIdMapBuilder = scala.collection.immutable.HashMap.newBuilder[Long, Array[Long]]

        for ((aggQc, childQCIds) <- childQCIdsByAggQC) {

          //Get all QPepIons with a value for current QChannel
          val filteredQPepIons = quantPepIonsUnion.filter(qpi => childQCIds.contains(qpi.quantChannelId))

          //For isobaric aggregation : Get all MQRepIons / QRepIons with a value for current QChannel
          val filteredQRepIons = mqReporterIonsUnion.flatMap(_.quantReporterIonMap.values).filter(qRi => childQCIds.contains(qRi.quantChannelId))
          val filteredMQRepIons = mqReporterIonsUnion.filter(mqr => mqr.quantReporterIonMap.values.exists(qRi => childQCIds.contains(qRi.quantChannelId)))

          if ( (isIsobaricTaggingMethod && filteredQRepIons.nonEmpty) || (!isIsobaricTaggingMethod && filteredQPepIons.nonEmpty)) {
            var templateQPepIon = templateMQPepIon.quantPeptideIonMap.values.find(qpi => childQCIds.contains(qpi.quantChannelId))
            // get all MQPeptideIon having a value for the specified qc
            var filteredMQPepIons = mqPepIonsByMQC.map(_._2).filter(mqp => mqp.quantPeptideIonMap.values.exists(qpi => childQCIds.contains(qpi.quantChannelId)))

            if (templateQPepIon.isEmpty) {
              logger.warn(s"Cannot find quant peptide ion from $childQCIds, look for the highest abundance instead")
              //VDS Warning maxBy may return wrong value if NaN
              templateQPepIon = Some(filteredQPepIons.maxBy(_.abundance))
            }

            if(isIsobaricTaggingMethod) {

              // Take minimum elution time as a reference for all quant peptide ions
              val firstMqReporterIon = filteredMQRepIons.minBy(_.scanNumber)
              val firstElutionTime = firstMqReporterIon.elutionTime
              val firstScanNumber = firstMqReporterIon.scanNumber
              val msQueryIds = filteredMQRepIons.map(_.msQueryId)

              val quantPepIonRawAbList = filteredQRepIons.map(_.rawAbundance).filter(!_.equals(Float.NaN))
              val quantPepIonAbList = filteredQRepIons.map(_.abundance).filter(!_.equals(Float.NaN))

              newQuantPepIons += QuantPeptideIon(
                rawAbundance = if (quantPepIonRawAbList.nonEmpty) quantPepIonRawAbList.sum else Float.NaN,
                abundance = if (quantPepIonAbList.nonEmpty) quantPepIonAbList.sum else Float.NaN,
                moz = templateQPepIon.get.moz,
                elutionTime = firstElutionTime,
                duration = 0,
                correctedElutionTime = firstElutionTime,
                scanNumber = firstScanNumber,
                peptideMatchesCount = filteredMQRepIons.size,
                ms2MatchingFrequency = None,
                bestPeptideMatchScore = templateQPepIon.get.bestPeptideMatchScore,
                predictedElutionTime = None,
                quantChannelId = aggQc.id,
                peptideId = Some(masterPepInst.peptideId),
                peptideInstanceId = Some(masterPepInst.id),
                msQueryIds = Some(msQueryIds.toArray),
                lcmsFeatureId = None,
                lcmsMasterFeatureId = None,
                unmodifiedPeptideIonId = None,
                selectionLevel = 2
              )
            } else {
              intensityComputation match {
                case AbundanceComputationMethod.MOST_INTENSE => {
                  filteredMQPepIons = mqPepIonsByMQC.map(_._2).filter(mqp => mqp.quantPeptideIonMap.values.exists(qpi => qpi == templateQPepIon.get))
                  newQuantPepIons += templateQPepIon.get.copy(quantChannelId = aggQc.id, peptideMatchesCount = filteredQPepIons.map(_.peptideMatchesCount).sum)
                }
                case AbundanceComputationMethod.INTENSITY_SUM => {
                  val quantPepIonWRawAb = filteredQPepIons.map(_.rawAbundance).filter(!_.equals(Float.NaN))
                  val quantPepIonWAb = filteredQPepIons.map(_.abundance).filter(!_.equals(Float.NaN))
                  //val quantPepIonWRT = filteredQPepIons.map(_.elutionTime).filter(!_.equals(Float.NaN))

                  newQuantPepIons += templateQPepIon.get.copy(
                    rawAbundance = if (quantPepIonWRawAb.nonEmpty) quantPepIonWRawAb.sum else Float.NaN,
                    abundance = if (quantPepIonWAb.nonEmpty) quantPepIonWAb.sum else Float.NaN,
                    peptideMatchesCount = filteredQPepIons.map(_.peptideMatchesCount).sum,
                    quantChannelId = aggQc.id)
                }
              }
            }
            mqPepIonIdMapBuilder += aggQc.id -> filteredMQPepIons.map(_.id).toArray
          } //end if filteredQPepIons.nonEmpty
        } //end for all Qchannels

        if (newQuantPepIons.isEmpty) {
          None
        } else {
          val mqRepIonArrOp = if(newMQuantRepIons.isEmpty) None else Some(newMQuantRepIons.toArray)
          val newMQPeptideIon = _buildMasterQuantPeptideIon(newMQPepIonId, templateMQPepIon, newQuantPepIons, Some(masterPepInst), mqRepIonArrOp)
          val prop = newMQPeptideIon.properties.getOrElse(MasterQuantPeptideIonProperties())
          prop.aggregatedMasterQuantPeptideIonIdMap = mqPepIonIdMapBuilder.result
          newMQPeptideIon.properties = Some(prop)
          Some(newMQPeptideIon)
        }
      //}
    }

//    //Summarize RepIons (same pep/same charge) to MQPepIon
//    private def _summarizeWithReporterIons(
//      mqPepIonsByMQC: Iterable[(Long, MasterQuantPeptideIon)],
//      quantChannelsMapping: Map[Long, QuantChannel],
//      masterPepInst: PeptideInstance): Option[MasterQuantPeptideIon] = {
//
//      val childQCIdsByAggQC = quantChannelsMapping.toSeq.groupBy(_._2).mapValues(_.map(_._1).seq)
//      val quantPepIonsUnion = mqPepIonsByMQC.flatMap(_._2.quantPeptideIonMap.values).toList
//      val templateMQPepIon = mqPepIonsByMQC.map(_._2).maxBy(_.calcRawAbundanceSum())
//      val mqReporterIonsUnion =  mqPepIonsByMQC.map(_._2).flatMap(_.masterQuantReporterIons)
//
//      val newQuantPepIons = new ArrayBuffer[QuantPeptideIon]()
//      val newMQPepIonId = MasterQuantPeptideIon.generateNewId()
//      val newMQuantRepIons = new ArrayBuffer[MasterQuantReporterIon]()
//      mqReporterIonsUnion.foreach(mqRepIon =>{
//        val newQRepMap = new mutable.LongMap[QuantReporterIon]()
//        mqRepIon.quantReporterIonMap.foreach( entry=> {
//          val qChId = quantChannelsMapping(entry._1).id
//          newQRepMap.put(qChId ,entry._2.copy(quantChannelId =qChId ))
//        })
//        newMQuantRepIons += mqRepIon.copy(
//                    id=MasterQuantReporterIon.generateNewId(),
//                    quantReporterIonMap = newQRepMap,
//                    masterQuantPeptideIonId = Some(newMQPepIonId))
//      })
//
//      //For MQPep properties info
//      val mqPepIonIdMapBuilder = scala.collection.immutable.HashMap.newBuilder[Long, Array[Long]]
//      for ((aggQCh, childQCIds) <- childQCIdsByAggQC) {
//
//        //Get all QPepIons with a value for current QChannel
//        val filteredQPepIons = quantPepIonsUnion.filter(qpi => childQCIds.contains(qpi.quantChannelId))
//
//        //Get all MQRepIons / QRepIons with a value for current QChannel
//        val filteredQRepIons = mqReporterIonsUnion.flatMap(_.quantReporterIonMap.values).filter(qRi => childQCIds.contains(qRi.quantChannelId))
//        val filteredMQRepIons = mqReporterIonsUnion.filter(mqr => mqr.quantReporterIonMap.values.exists(qRi => childQCIds.contains(qRi.quantChannelId)))
//
//        if (filteredQRepIons.nonEmpty) {
//          var templateQPepIon = templateMQPepIon.quantPeptideIonMap.values.find(qpi => childQCIds.contains(qpi.quantChannelId))
//          // get all MQPeptideIon having a value for the specified qc
//          var filteredMQPepIons = mqPepIonsByMQC.map(_._2).filter(mqp => mqp.quantPeptideIonMap.values.exists(qpi => childQCIds.contains(qpi.quantChannelId)))
//
//          if (templateQPepIon.isEmpty) {
//            logger.warn(s"Cannot find quant peptide ion from $childQCIds, look for the highest abundance instead")
//            //VDS Warning maxBy may return wrong value if NaN
//            templateQPepIon = Some(filteredQPepIons.maxBy(_.abundance))
//          }
//
//          // Take minimum elution time as a reference for all quant peptide ions
//          val firstMqReporterIon = filteredMQRepIons.minBy(_.scanNumber)
//          val firstElutionTime = firstMqReporterIon.elutionTime
//          val firstScanNumber = firstMqReporterIon.scanNumber
//
//          val msQueryIds = filteredMQRepIons.map(_.msQueryId)
//
//          val quantPepIonRawAbList = filteredQRepIons.map(_.rawAbundance).filter(!_.equals(Float.NaN))
//          val quantPepIonAbList = filteredQRepIons.map(_.abundance).filter(!_.equals(Float.NaN))
//
//          newQuantPepIons += QuantPeptideIon(
//            rawAbundance = if (quantPepIonRawAbList.nonEmpty) quantPepIonRawAbList.sum else Float.NaN,
//            abundance = if (quantPepIonAbList.nonEmpty) quantPepIonAbList.sum else Float.NaN,
//            moz = templateQPepIon.get.moz,
//            elutionTime = firstElutionTime,
//            duration = 0,
//            correctedElutionTime = firstElutionTime,
//            scanNumber = firstScanNumber,
//            peptideMatchesCount = filteredMQRepIons.size,
//            ms2MatchingFrequency = None,
//            bestPeptideMatchScore = templateQPepIon.get.bestPeptideMatchScore,
//            predictedElutionTime = None,
//            quantChannelId = aggQCh.id,
//            peptideId = Some(masterPepInst.peptideId),
//            peptideInstanceId = Some(masterPepInst.id),
//            msQueryIds = Some(msQueryIds.toArray),
//            lcmsFeatureId = None,
//            lcmsMasterFeatureId = None,
//            unmodifiedPeptideIonId = None, // TODO: set this value ???
//            selectionLevel = 2
//          )
//
//          mqPepIonIdMapBuilder += aggQCh.id -> filteredMQPepIons.map(_.id).toArray
//
//        } // end if  filteredQPepIons.nonEmpty
//      } //End for all QChannels
//
//      if (newQuantPepIons.isEmpty) {
//        None
//      } else {
//
//        // Map quant peptide ions by feature id or feature id
//        val qPepIonByQcId = newQuantPepIons.toLongMapWith(qpi => qpi.quantChannelId -> qpi)
//        require(qPepIonByQcId.size == newQuantPepIons.length, "duplicated feature detected in quant peptide ions")
//
//        val newMQPeptideIon = templateMQPepIon.copy(
//              id = newMQPepIonId,
//              masterQuantPeptideId = 0,
//              resultSummaryId = quantMergedRsm.id,
//              peptideInstanceId =Some(masterPepInst.id),
//              quantPeptideIonMap = qPepIonByQcId,
//              peptideMatchesCount = newQuantPepIons.map(_.peptideMatchesCount).sum,
//              selectionLevel = 2,
//              masterQuantReporterIons = newMQuantRepIons.toArray
//        )
//        val prop = newMQPeptideIon.properties.getOrElse(MasterQuantPeptideIonProperties())
//        prop.aggregatedMasterQuantPeptideIonIdMap = mqPepIonIdMapBuilder.result
//        newMQPeptideIon.properties = Some(prop)
//        Some(newMQPeptideIon )
//      }
//  }


    private def _buildMasterQuantPeptideIon(newMQPepIonId: Long,
      mqPepIonTemplate: MasterQuantPeptideIon,
      qPepIons: Seq[QuantPeptideIon],
      masterPepInstAsOpt: Option[PeptideInstance],
      newMasterQuantReporterIons: Option[Array[MasterQuantReporterIon]]
    ): MasterQuantPeptideIon = {

      require(qPepIons != null && qPepIons.length > 0, "qPepIons must not be empty")

      // Map quant peptide ions by feature id or feature id
      val qPepIonByQcId = qPepIons.toLongMapWith(qpi => qpi.quantChannelId -> qpi)
      require(qPepIonByQcId.size == qPepIons.length, "duplicated feature detected in quant peptide ions")


      mqPepIonTemplate.copy(
        id = newMQPepIonId,
        masterQuantPeptideId = 0,
        resultSummaryId = quantMergedRsm.id,
        peptideInstanceId = masterPepInstAsOpt.map(_.id),
        quantPeptideIonMap = qPepIonByQcId,
        peptideMatchesCount = qPepIons.map(_.peptideMatchesCount).sum,
        selectionLevel = 2,
        masterQuantReporterIons = if(newMasterQuantReporterIons.isDefined) newMasterQuantReporterIons.get else Array()
      )
    }

  } // End of MqPepsComputer Class

  override def computeMasterQuantPeptides(masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]): Array[MasterQuantPeptide] = {
    val mqPepsComputer = new MQPepsComputer(masterQuantChannel, quantMergedRSM)
    mqPepsComputer.computeMasterQuantPeptides()
  }

}
