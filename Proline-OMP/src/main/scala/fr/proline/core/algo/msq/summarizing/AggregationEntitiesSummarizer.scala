package fr.proline.core.algo.msq.summarizing

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.proline.core.algo.msq.config.AbundanceComputationMethod
import fr.proline.core.om.model.msi.{PeptideInstance, ResultSummary}
import fr.proline.core.om.model.msq._

import scala.collection.mutable.ArrayBuffer

class AggregationEntitiesSummarizer(
  childQuantRSMbychildMQCId: Map[Long, QuantResultSummary],
  quantChannelsMapping: Map[Long, QuantChannel],
  intensityComputation: AbundanceComputationMethod.Value = AbundanceComputationMethod.INTENSITY_SUM
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {


  private class MQPepsComputer(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRsm: ResultSummary
  ) extends LazyLogging {

    val masterPepInstByPepId = quantMergedRsm.peptideInstances.toLongMapWith(pi => pi.peptide.id -> pi)
    // Link each child masterQuantPeptide to it's own MasterQuantChannel Id
    val childMQPeptidesByChildMQCId = childQuantRSMbychildMQCId.map { case (mqcId, quantRSM) => quantRSM.masterQuantPeptides.map((mqcId, _)) }.flatten
    // ... then build a Map peptideIds -> Map[ MasterQuantChannel, MasterQuantPeptide ]
    val childMQPeptidesByPeptideId = childMQPeptidesByChildMQCId.groupBy(_._2.peptideInstance.get.peptideId).map { case (pepId, pairs) => (pepId, pairs.toMap) }

    def computeMasterQuantPeptides(): Array[MasterQuantPeptide] = {

      val newMasterQuantPeptides = new ArrayBuffer[MasterQuantPeptide](childMQPeptidesByPeptideId.size)

      for ((peptideId, masterPepInst) <- masterPepInstByPepId) {

        val masterQuantPeptidesOpt = childMQPeptidesByPeptideId.get(peptideId)

        if (masterQuantPeptidesOpt.isDefined) {
          val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]()
          val mqPepIonsByChargeAndMQC = masterQuantPeptidesOpt.get.map{ case(k,v) => v.masterQuantPeptideIons.map( (k, _ ))}.flatten.groupBy(_._2.charge)
          for (charge <- mqPepIonsByChargeAndMQC.keys) {
            val mqPepIonOpt = _summarize(mqPepIonsByChargeAndMQC(charge), quantChannelsMapping, masterPepInst)
            if (mqPepIonOpt.isDefined) mqPepIons += mqPepIonOpt.get
          }
          if (!mqPepIons.isEmpty) {
            newMasterQuantPeptides += BuildMasterQuantPeptide(mqPepIons, Some(masterPepInst), quantMergedRsm.id)
          }
        }
      }
      newMasterQuantPeptides.toArray
    }

    private def _summarize(
      mqPepIonsByMQC: Iterable[(Long, MasterQuantPeptideIon)],
      quantChannelsMapping: Map[Long, QuantChannel],
      masterPepInst: PeptideInstance): Option[MasterQuantPeptideIon] = {

      val newQuantPepIons = new ArrayBuffer[QuantPeptideIon]()
      val childQCIdsByQC = quantChannelsMapping.toSeq.groupBy(_._2).mapValues(_.map(_._1).seq)
      val quantPepIons = mqPepIonsByMQC.flatMap(_._2.quantPeptideIonMap.values).toList
      val bestMQPepIon = mqPepIonsByMQC.map(_._2).maxBy(_.calcRawAbundanceSum())

      val mqPepIonIdMapBuilder = scala.collection.immutable.HashMap.newBuilder[Long,Array[Long]]

      for ((qc, childQCIds) <- childQCIdsByQC) {
        val filteredQPepIons = quantPepIons.filter(qpi => childQCIds.contains(qpi.quantChannelId))

        if (!filteredQPepIons.isEmpty) {
          var bestQPepIon = bestMQPepIon.quantPeptideIonMap.values.find(qpi => childQCIds.contains(qpi.quantChannelId))
          // get all MQPeptideIon having a value for the specified qc
          var filteredMQPepIons = mqPepIonsByMQC.map(_._2).filter(mqp => !mqp.quantPeptideIonMap.values.filter( qpi => childQCIds.contains(qpi.quantChannelId)).isEmpty)

          if (!bestQPepIon.isDefined) {
            logger.warn(s"Cannot find quant peptide ion from $childQCIds, look for the highest abundance instead")
            bestQPepIon = Some(filteredQPepIons.maxBy(_.abundance))
          }

          intensityComputation match {
            case AbundanceComputationMethod.MOST_INTENSE => {
              filteredMQPepIons = mqPepIonsByMQC.map(_._2).filter(mqp => !mqp.quantPeptideIonMap.values.filter( qpi => qpi == bestQPepIon.get).isEmpty)
              newQuantPepIons += bestQPepIon.get.copy(quantChannelId = qc.id)
            }
            case AbundanceComputationMethod.INTENSITY_SUM => {
              newQuantPepIons += bestQPepIon.get.copy(rawAbundance = filteredQPepIons.map(_.rawAbundance).sum, abundance = filteredQPepIons.map(_.abundance).sum, quantChannelId = qc.id)
            }
          }
          mqPepIonIdMapBuilder += qc.id -> filteredMQPepIons.map(_.id).toArray
        }
      }

      if (newQuantPepIons.isEmpty) {
        None
      } else  {
        val newMQPeptideIon = _buildMasterQuantPeptideIon(bestMQPepIon, newQuantPepIons, Some(masterPepInst))
        newMQPeptideIon.properties.getOrElse(MasterQuantPeptideIonProperties()).aggregatedMasterQuantPeptideIonIdMap = mqPepIonIdMapBuilder.result
        Some(newMQPeptideIon)
      }
    }

    private def _buildMasterQuantPeptideIon(
      mqPepIonTemplate: MasterQuantPeptideIon,
      qPepIons: Seq[QuantPeptideIon],
      masterPepInstAsOpt: Option[PeptideInstance]
    ): MasterQuantPeptideIon = {

      require(qPepIons != null && qPepIons.length > 0, "qPepIons must not be empty")

      // Map quant peptide ions by feature id or feature id
      val qPepIonByQcId = qPepIons.toLongMapWith(qpi => qpi.quantChannelId -> qpi)
      require(qPepIonByQcId.size == qPepIons.length, "duplicated feature detected in quant peptide ions")


      mqPepIonTemplate.copy(
        id = MasterQuantPeptideIon.generateNewId(),
        masterQuantPeptideId = 0,
        resultSummaryId = quantMergedRsm.id,
        peptideInstanceId = masterPepInstAsOpt.map(_.id),
        quantPeptideIonMap = qPepIonByQcId
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