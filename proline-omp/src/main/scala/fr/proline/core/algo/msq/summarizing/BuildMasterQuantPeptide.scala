package fr.proline.core.algo.msq.summarizing

import scala.collection.mutable.LongMap
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

object BuildMasterQuantPeptide {
  
  def apply(
    mqPepIons: Seq[MasterQuantPeptideIon],
    masterPepInstAsOpt: Option[PeptideInstance],
    quantRsmId: Long
  ): MasterQuantPeptide = {
    require( mqPepIons != null && mqPepIons.length > 0, "mqPepIons must not be empty")
    
    // Generate and update master quant peptide id
    val mqPeptideId = MasterQuantPeptide.generateNewId
    mqPepIons.foreach { mqPepIon =>
      mqPepIon.masterQuantPeptideId = mqPeptideId
    }
    
    // Filter MQ peptide ions using the selection level
    var filteredMQPepIons = mqPepIons.filter(_.selectionLevel >= 2 )
    // Fall back to input list if none MQ peptide is selected
    if( filteredMQPepIons.isEmpty ) filteredMQPepIons = mqPepIons

    // TODO: allows to sum charge states
    //val bestMQPepIon = filteredMQPepIons.maxBy( _.calcAbundanceSum )
    //val bestMQPepIon = filteredMQPepIons.maxBy( _.calcFrequency(qcCount) )
    
    val bestMQPepIon = getBestPeptideIon(filteredMQPepIons)
    
    val quantPepByQcId = new LongMap[QuantPeptide](bestMQPepIon.quantPeptideIonMap.size)
    val peptideIonMap = filteredMQPepIons.flatMap(_.quantPeptideIonMap.map(_._2)).groupBy(_.quantChannelId)
    
    // Procedure to use if we want to sum the filteredMQPepIons
    /*for( (qcId,quantPepIons) <- peptideIonMap ) {
      // Build the quant peptide
      val qp = new QuantPeptide(
        rawAbundance = quantPepIons.map(_.rawAbundance).sum,
        abundance = quantPepIons.map(_.abundance).sum,
        elutionTime = quantPepIons.map(_.elutionTime).sum / quantPepIons.length,
        peptideMatchesCount = quantPepIon.peptideMatchesCount,
        quantChannelId = qcId,
        selectionLevel = 2
      )
      
      quantPepByQcId += qcId -> qp
    }*/
    
    // Procedure to use if we want to use the best bestMQPepIon
    for( (qcId,quantPepIon) <- bestMQPepIon.quantPeptideIonMap ) {
      
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

    new MasterQuantPeptide(
      id = mqPeptideId,
      peptideInstance = masterPepInstAsOpt,
      quantPeptideMap = quantPepByQcId,
      masterQuantPeptideIons = mqPepIons.toArray,
      selectionLevel = bestMQPepIon.selectionLevel,
      resultSummaryId = quantRsmId
    )

  }
  
  def getBestPeptideIon(filteredMQPepIons: Seq[MasterQuantPeptideIon]): MasterQuantPeptideIon = {
        // Group master quant peptide ions by number of identified quant. channels
    val mqPepIonsByIdentCount = filteredMQPepIons.groupBy(_.countIdentifications)
    val maxIdentCount = mqPepIonsByIdentCount.keys.max
    val mqPepIonsWithMaxIdentifications = mqPepIonsByIdentCount(maxIdentCount)

    val bestMQPepIon = if (mqPepIonsWithMaxIdentifications.size == 1 ) mqPepIonsWithMaxIdentifications.head
    else {
      
      // Group master quant peptide ions by number of peptide matches count
      val mqPepIonsByPepMatchesCount = mqPepIonsWithMaxIdentifications.groupBy(_.peptideMatchesCount)
      val maxPepMatchesCount = mqPepIonsByPepMatchesCount.keys.max
      val mqPepIonsWithMaxPepMatchesCount = mqPepIonsByPepMatchesCount(maxPepMatchesCount)
      
      if (mqPepIonsWithMaxPepMatchesCount == 1 ) mqPepIonsWithMaxIdentifications.head
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