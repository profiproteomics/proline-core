package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.api.service.IService
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msi.{PeptideInstance,ProteinSet}
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.context.IExecutionContext

class ExportMasterQuantPeptides(
  val execCtx: IExecutionContext,
  val masterQuantChannelId: Long,
  val outputFile: File,
  val expDesign: ExperimentalDesign
) extends AbstractQuantRsmExporter {
  
  // TODO: retrieve the right value
  protected val groupSetupNumber = 1

  protected val pepHeaders = "sequence ptms prot_set_count".split(" ")
  protected val mqPepHeaders = "quant_peptide_id best_peptide_match_score selection_level".split(" ")  
  protected val ratioDefs = expDesign.groupSetupByNumber(groupSetupNumber).ratioDefinitions
  
  // Create some mappings
  protected val pepMatchById = Map() ++ quantRSM.resultSummary.resultSet.get.peptideMatchById
  protected val protSetByPepInst = Map()++ quantRSM.resultSummary.proteinSets.flatMap( protSet => protSet.peptideSet.getPeptideInstances.map( pi => pi.id -> protSet ) )
  
  protected val mqPepCellsById = {
    
    val tmpMqPepCellsById = new HashMap[Long,ArrayBuffer[Any]]
    
    quantRSM.masterQuantPeptides.foreach { mqPep =>
      
      val mqPepCells = new ArrayBuffer[Any]
      
      // TODO: check if this is really the best score
      val scoreOpt = mqPep.peptideInstance.map( pi => pepMatchById(pi.bestPeptideMatchId).score )
      
      // Append master quant peptide data
      mqPepCells ++= Array(mqPep.id,scoreOpt.getOrElse(""), mqPep.selectionLevel)
      
      // Append quant peptide data for each condition
      /*val qPepCellsByQcId = new HashMap[Long,Seq[Any]]
      for( (qcId, qPep) <- mqPep.quantPeptideMap ) {
        val qPepCells = List(
          qPep.rawAbundance
        )
        
        qPepCellsByQcId += qcId -> qPepCells
      }
      
      for(qcId <- qcIds) {
        val qPepCellsOpt = qPepCellsByQcId.get(qcId)
        if( qPepCellsOpt.isDefined ) mqPepCells ++= qPepCellsOpt.get
        else mqPepCells ++= Array.fill(qPepHeaders.length)("")
      }*/
      
      val quantPepMap = mqPep.quantPeptideMap
      val abundanceBuffer = new ArrayBuffer[Any]
      val rawAbundanceBuffer = new ArrayBuffer[Any]
      val psmCountBuffer = new ArrayBuffer[Any]
      
      for(qcId <- qcIds) {
        val qPepOpt = quantPepMap.get(qcId)
        
        if( qPepOpt.isDefined ) {
          val qPep = qPepOpt.get
          abundanceBuffer += qPep.abundance
          rawAbundanceBuffer += qPep.rawAbundance
          psmCountBuffer += qPep.peptideMatchesCount
        } else {
          abundanceBuffer += ""
          rawAbundanceBuffer += ""
          psmCountBuffer += ""
        }
      }
      
      // Add raw abundances
      mqPepCells ++= rawAbundanceBuffer
      
      // Add abundances
      mqPepCells ++= abundanceBuffer
      
      // Add PSM counts
      mqPepCells ++= psmCountBuffer
      
      tmpMqPepCellsById += mqPep.id -> mqPepCells
    }
    
    tmpMqPepCellsById
  }
  
  protected def writeRows( fileWriter: PrintWriter ) {
    
    // Iterate over master quant peptides to export them
    quantRSM.masterQuantPeptides.foreach { mqPep =>
      
      val row = new ArrayBuffer[Any]
      
      // Append protein set and peptide data if they are defined
      // TODO: stringify peptide instance data
      if( mqPep.peptideInstance.isDefined) {
        val pepInstId = mqPep.peptideInstance.get.id
        appendProtSetAndPepInstCells(row,protSetByPepInst.get(pepInstId),mqPep.peptideInstance)
      } else {
        appendProtSetAndPepInstCells(row,None,None)
      }
      
      row ++= mqPepCellsById(mqPep.id)
      
      // Add profile statistics
      val mqPepProfile = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get(groupSetupNumber.toString)
      val stats = this.stringifyRatiosStats(mqPepProfile.getRatios)
      row ++= stats
      
      fileWriter.println(row.mkString("\t"))
      fileWriter.flush()
      
    }

  }
  
  protected def appendPepInstCells(row: ArrayBuffer[Any], pepInstOpt: Option[PeptideInstance]) {
    if( pepInstOpt.isDefined ) {
      val pepInst = pepInstOpt.get
      val peptide = pepInst.peptide
      row ++= Array(peptide.sequence,peptide.readablePtmString,pepInst.proteinSetsCount)
    }
    else row ++= Array.fill(pepHeaders.length)("")
  }
  
  protected def appendProtSetAndPepInstCells(row: ArrayBuffer[Any], protSetOpt: Option[ProteinSet], pepInstOpt: Option[PeptideInstance]) {
    appendProtSetCells(row,protSetOpt)
    appendPepInstCells(row,pepInstOpt)
  }
  
  protected def mkProtSetAndQPepHeaderCols( quantChannelCount: Int ): Seq[String] = {
    val rowHeaders = new ArrayBuffer[String] ++ protSetHeaders ++ pepHeaders ++ mqPepHeaders
    
    for( i <- 1 to quantChannelCount ) rowHeaders += "raw_abundance_"+i
    for( i <- 1 to quantChannelCount ) rowHeaders += "abundance_"+i
    for( i <- 1 to quantChannelCount ) rowHeaders += "psm_count_"+i
    
    rowHeaders
  }
  
  protected def mkRowHeader( quantChannelCount: Int ): String = {
    
    val rowHeaders = new ArrayBuffer[String]()
    rowHeaders ++= mkProtSetAndQPepHeaderCols( quantChannelCount )

    for( r <- ratioDefs ) rowHeaders ++= statHeaders.map( _ + ("_g" + r.numeratorGroupNumber +" _vs_g"+ r.denominatorGroupNumber) )
    
    rowHeaders.mkString("\t")
  }

}