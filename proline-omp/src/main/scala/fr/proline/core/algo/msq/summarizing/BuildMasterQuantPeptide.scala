package fr.proline.core.algo.msq.summarizing

import fr.proline.core.algo.lcms.MqPepIonAbundanceSummarizingMethod
import fr.proline.core.om.model.SelectionLevel
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

import scala.collection.mutable
import scala.collection.mutable.LongMap

object BuildMasterQuantPeptide {

  def apply(
             mqPepIons: Seq[MasterQuantPeptideIon],
             masterPepInstAsOpt: Option[PeptideInstance],
             quantRsmId: Long
           ): MasterQuantPeptide = {
    apply(mqPepIons, masterPepInstAsOpt, quantRsmId, MqPepIonAbundanceSummarizingMethod.BEST_ION)
  }

  def apply(
             mqPepIons: Seq[MasterQuantPeptideIon],
             masterPepInstAsOpt: Option[PeptideInstance],
             quantRsmId: Long,
             pepIonAbundanceSummarizingMethod: MqPepIonAbundanceSummarizingMethod.Value
           ): MasterQuantPeptide = {
    require(mqPepIons != null && mqPepIons.length > 0, "mqPepIons must not be empty")

    // Generate and update master quant peptide id
    val mqPeptideIonSelLById = new mutable.HashMap[Long, Int]()
    val mqPeptideId = MasterQuantPeptide.generateNewId
    mqPepIons.foreach { mqPepIon =>
      mqPepIon.masterQuantPeptideId = mqPeptideId
      mqPeptideIonSelLById.put(mqPepIon.id,mqPepIon.selectionLevel)
    }

    // Filter MQ peptide ions using the selection level
    var filteredMQPepIons = mqPepIons.filter(_.selectionLevel >= SelectionLevel.SELECTED_AUTO)
    // Fall back to input list if none MQ peptide is selected
    if (filteredMQPepIons.isEmpty) filteredMQPepIons = mqPepIons
    val quantPepByQcId = new LongMap[QuantPeptide]()
    // created quantPeptides
    var mqPepSelectionLevel = SelectionLevel.SELECTED_AUTO

    val summarizerProperties = new PepIonAbundanceSummarizingConfig(methodName = pepIonAbundanceSummarizingMethod.toString)

    pepIonAbundanceSummarizingMethod match {

      case MqPepIonAbundanceSummarizingMethod.BEST_ION => {

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
        //Reset selectionlevel map
        mqPeptideIonSelLById.clear()
        mqPepIons.foreach { mqPepIon =>
          if(mqPepIon.id.equals(bestMQPepIon.id))
            mqPeptideIonSelLById.put(mqPepIon.id,SelectionLevel.SELECTED_AUTO)
          else if(mqPepIon.selectionLevel>=SelectionLevel.SELECTED_AUTO)
            mqPeptideIonSelLById.put(mqPepIon.id,SelectionLevel.DESELECTED_AUTO)
        }

        summarizerProperties.mqPeptideIonSelLevelById = mqPeptideIonSelLById
      }

      case MqPepIonAbundanceSummarizingMethod.SUM => {

        //Group PepIon by quant channel ids
        val peptideIonsByQChId: Map[Long, Seq[QuantPeptideIon]] = filteredMQPepIons.flatMap(_.quantPeptideIonMap.values).groupBy(_.quantChannelId)

        for ((qcId, quantPepIons) <- peptideIonsByQChId) {
          val quantPepIonWRawAb = quantPepIons.map(_.rawAbundance).filter(!_.equals(Float.NaN))
          val quantPepIonWAb = quantPepIons.map(_.abundance).filter(!_.equals(Float.NaN))
          val quantPepIonWRT = quantPepIons.map(_.elutionTime).filter(!_.equals(Float.NaN))
          // Build the quant peptide
          val qp = new QuantPeptide(
            rawAbundance = if (quantPepIonWRawAb.nonEmpty)quantPepIonWRawAb.sum else Float.NaN,
            abundance = if (quantPepIonWAb.nonEmpty)quantPepIonWAb.sum else Float.NaN ,
            elutionTime = if(quantPepIonWRT.nonEmpty) quantPepIonWRT.sum / quantPepIons.length else Float.NaN,
            peptideMatchesCount = quantPepIons.map(_.peptideMatchesCount).sum,
            quantChannelId = qcId,
            selectionLevel = SelectionLevel.SELECTED_AUTO
          )
          quantPepByQcId += qcId -> qp
        }
        mqPepSelectionLevel = filteredMQPepIons.head.selectionLevel
        summarizerProperties.mqPeptideIonSelLevelById = mqPeptideIonSelLById
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