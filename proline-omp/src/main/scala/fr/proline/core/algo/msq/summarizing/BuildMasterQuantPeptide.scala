package fr.proline.core.algo.msq.summarizing

import fr.proline.core.algo.lcms.PepIonAbundanceSummarizingMethod

import scala.collection.mutable.LongMap
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

object BuildMasterQuantPeptide {

  def apply(
             mqPepIons: Seq[MasterQuantPeptideIon],
             masterPepInstAsOpt: Option[PeptideInstance],
             quantRsmId: Long
           ): MasterQuantPeptide = {
    apply(mqPepIons, masterPepInstAsOpt, quantRsmId, PepIonAbundanceSummarizingMethod.BEST_ION)
  }

  def apply(
             mqPepIons: Seq[MasterQuantPeptideIon],
             masterPepInstAsOpt: Option[PeptideInstance],
             quantRsmId: Long,
             pepIonAbundanceSummarizingMethod: PepIonAbundanceSummarizingMethod.Value
           ): MasterQuantPeptide = {
    require(mqPepIons != null && mqPepIons.length > 0, "mqPepIons must not be empty")

    // Generate and update master quant peptide id
    val mqPeptideId = MasterQuantPeptide.generateNewId
    mqPepIons.foreach { mqPepIon =>
      mqPepIon.masterQuantPeptideId = mqPeptideId
    }

    // Filter MQ peptide ions using the selection level
    var filteredMQPepIons = mqPepIons.filter(_.selectionLevel >= 2)
    // Fall back to input list if none MQ peptide is selected
    if (filteredMQPepIons.isEmpty) filteredMQPepIons = mqPepIons
    val quantPepByQcId = new LongMap[QuantPeptide]()
    // created quantPeptides
    var mqPepSelectionLevel = 2

    val summarizerProperties = new PepIonAbundanceSummarizingConfig(methodName = pepIonAbundanceSummarizingMethod.toString)


    pepIonAbundanceSummarizingMethod match {

      case PepIonAbundanceSummarizingMethod.BEST_ION => {

        val bestMQPepIon = getBestPeptideIon(filteredMQPepIons)
        for ((qcId, quantPepIon) <- bestMQPepIon.quantPeptideIonMap) {

          // Build the quant peptide
          val qp = new QuantPeptide(
            rawAbundance = quantPepIon.rawAbundance,
            abundance = quantPepIon.abundance,
            elutionTime = quantPepIon.elutionTime,
            peptideMatchesCount = quantPepIon.peptideMatchesCount,
            quantChannelId = qcId,
            selectionLevel = quantPepIon.selectionLevel
          )
          quantPepByQcId += qcId -> qp
        }
        mqPepSelectionLevel = bestMQPepIon.selectionLevel
        summarizerProperties.getMethodParams += ("best_ion_id" -> bestMQPepIon.id.toString)
      }

      case PepIonAbundanceSummarizingMethod.SUM => {

        //Group PepIon by quanf channel ids
        val peptideIonMap: Map[Long, Seq[QuantPeptideIon]] = filteredMQPepIons.flatMap(_.quantPeptideIonMap.map(_._2)).groupBy(_.quantChannelId)
        val countByQChannel = collection.mutable.Map.empty[Long,Int]

        for ((qcId, quantPepIons) <- peptideIonMap) {
          val quantPepIonWRawAb = quantPepIons.map(_.rawAbundance).filter(!_.equals(Float.NaN))
          val quantPepIonWAb = quantPepIons.map(_.abundance).filter(!_.equals(Float.NaN))
          val quantPepIonWRT = quantPepIons.map(_.elutionTime).filter(!_.equals(Float.NaN))
          // Build the quant peptide
          val qp = new QuantPeptide(
            rawAbundance = if (!quantPepIonWRawAb.isEmpty)quantPepIonWRawAb.sum else Float.NaN,
            abundance = if (!quantPepIonWAb.isEmpty)quantPepIonWAb.sum else Float.NaN ,
            elutionTime = if(!quantPepIonWRT.isEmpty) quantPepIonWRT.sum / quantPepIons.length else Float.NaN,
            peptideMatchesCount = quantPepIons.map(_.peptideMatchesCount).sum,
            quantChannelId = qcId,
            selectionLevel = 2
          )
          countByQChannel += qcId -> quantPepIons.length
          quantPepByQcId += qcId -> qp
        }
        mqPepSelectionLevel = filteredMQPepIons.head.selectionLevel
        summarizerProperties.getMethodParams += ("summed_ions_count_by_quant_channel" -> countByQChannel.toString)
      }
    }

    val mqPepProperties = MasterQuantPeptideProperties(
      mqPepIonAbundanceSummarizingConfig = Some(summarizerProperties)
    )

    new MasterQuantPeptide(
      id = mqPeptideId,
      peptideInstance = masterPepInstAsOpt,
      quantPeptideMap = quantPepByQcId,
      masterQuantPeptideIons = mqPepIons.toArray,
      selectionLevel = mqPepIons.head.selectionLevel,
      resultSummaryId = quantRsmId,
      properties = Some(mqPepProperties)
    )

  }

  def getBestPeptideIon(filteredMQPepIons: Seq[MasterQuantPeptideIon]): MasterQuantPeptideIon = {
    // Group master quant peptide ions by number of identified quant. channels
    val mqPepIonsByIdentCount = filteredMQPepIons.groupBy(_.countIdentifications)
    val maxIdentCount = mqPepIonsByIdentCount.keys.max
    val mqPepIonsWithMaxIdentifications = mqPepIonsByIdentCount(maxIdentCount)

    val bestMQPepIon = if (mqPepIonsWithMaxIdentifications.size == 1) mqPepIonsWithMaxIdentifications.head
    else {

      // Group master quant peptide ions by number of peptide matches count
      val mqPepIonsByPepMatchesCount = mqPepIonsWithMaxIdentifications.groupBy(_.peptideMatchesCount)
      val maxPepMatchesCount = mqPepIonsByPepMatchesCount.keys.max
      val mqPepIonsWithMaxPepMatchesCount = mqPepIonsByPepMatchesCount(maxPepMatchesCount)

      if (mqPepIonsWithMaxPepMatchesCount == 1) mqPepIonsWithMaxIdentifications.head
      else {
        // More than one MQPepIon with same max peptide matches count
        // Get MQPepIon with max defined abundances
        val mqPepIonsByDefAbCount = mqPepIonsWithMaxPepMatchesCount.groupBy(_.countDefinedRawAbundances())
        val maxDefAbCount = mqPepIonsByDefAbCount.keys.max
        val mqPepIonsWithMaxDefAbundances = mqPepIonsByDefAbCount(maxDefAbCount)

        // Sort on Abundance Sum if still equality
        mqPepIonsWithMaxDefAbundances.maxBy(_.calcAbundanceSum())
      }
    }
    bestMQPepIon
  }

}