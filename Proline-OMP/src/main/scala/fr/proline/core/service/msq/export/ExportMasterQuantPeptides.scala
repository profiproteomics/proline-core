package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.api.service.IService
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msi.{PeptideInstance,ProteinSet}
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.context.IExecutionContext

class ExportMasterQuantPeptides(
  val execCtx: IExecutionContext,
  val masterQuantChannelId: Long,
  val outputFile: File
) extends XQuantRsmExporter {

  protected val pepHeaders = "sequence".split(" ")
  protected val mqPepHeaders = "quant_peptide_id selection_level".split(" ")  
  protected val qPepHeaders = "raw_abundance".split(" ")
  
  // Create some mappings   
  protected val protSetByPepInst = Map()++ quantRSM.resultSummary.proteinSets.flatMap( protSet => protSet.peptideSet.getPeptideInstances.map( pi => pi.id -> protSet ) )
  
  protected val mqPepCellsById = {
    
    val tmpMqPepCellsById = new HashMap[Long,ArrayBuffer[Any]]
    
    quantRSM.masterQuantPeptides.foreach { mqPep =>
      
      val mqPepCells = new ArrayBuffer[Any]
    
      // Append master quant peptide data
      mqPepCells ++= Array(mqPep.id,mqPep.selectionLevel)
      
      // Append quant peptide data for each condition
      val qPepCellsByQcId = new HashMap[Long,Seq[Any]]
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
      }
      
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
      
      fileWriter.println(row.mkString("\t"))
      fileWriter.flush()
      
    }

  }
  
  protected def appendPepInstCells(row: ArrayBuffer[Any], pepInstOpt: Option[PeptideInstance]) {
    if( pepInstOpt.isDefined ) row ++= Array(pepInstOpt.get.peptide.sequence)
    else row ++= Array.fill(pepHeaders.length)("")
  }
  
  protected def appendProtSetAndPepInstCells(row: ArrayBuffer[Any], protSetOpt: Option[ProteinSet], pepInstOpt: Option[PeptideInstance]) {
    appendProtSetCells(row,protSetOpt)
    appendPepInstCells(row,pepInstOpt)
  }
  
  protected def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ protSetHeaders ++ pepHeaders ++ mqPepHeaders
    for( i <- 1 to quantChannelCount ) rowHeaders ++= ( qPepHeaders.map(_+"_"+i) )
    rowHeaders.mkString("\t")
  }

}