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

class ExportMasterQuantPeptides( execCtx: IExecutionContext, masterQuantChannelId: Long, outputFile: File ) extends IService {
  
  val locale = java.util.Locale.ENGLISH
  val udsDbHelper = new UdsDbHelper(execCtx.getUDSDbConnectionContext())

  val proSetHeaders = "AC description".split(" ")
  val pepHeaders = "sequence".split(" ")
  val mqPepHeaders = "quant_peptide_id selection_level".split(" ")  
  val qPepHeaders = "raw_abundance".split(" ")
  
  val quantRsmId = udsDbHelper.getMasterQuantChannelQuantRsmId( masterQuantChannelId )
  val qcIds = udsDbHelper.getQuantChannelIds(masterQuantChannelId)

  val quantRSM = {
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      execCtx.getMSIDbConnectionContext,
      execCtx.getPSDbConnectionContext,
      execCtx.getUDSDbConnectionContext
    )
    quantRsmProvider.getQuantResultSummary(quantRsmId.get, qcIds, true).get
  }
  
  // Create some mappings   
  val protSetByPepInst = Map()++ quantRSM.resultSummary.proteinSets.flatMap( protSet => protSet.peptideSet.getPeptideInstances.map( pi => pi.id -> protSet ) )
  val protMatchById = quantRSM.resultSummary.resultSet.get.proteinMatchById
  
  val protSetCellsById = {
    
    val tmpProtSetCellsById = new HashMap[Long,ArrayBuffer[Any]]
    for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
      val protMatch = protMatchById( mqProtSet.proteinSet.proteinMatchIds(0) )
      val protSetCells = new ArrayBuffer[Any]
      protSetCells += protMatch.accession
      protSetCells += protMatch.description
      
      tmpProtSetCellsById += mqProtSet.id -> protSetCells
    }
    
    tmpProtSetCellsById
  }
  
  val mqPepCellsById = {
    
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
  
  def runService() = {
          
    // Create a file writer and print the header
    val fileWriter = new PrintWriter(new FileOutputStream(outputFile))
    fileWriter.println(mkRowHeader(qcIds.length))
    fileWriter.flush()
    
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

    fileWriter.close()
    
    true
  }
  
  def appendProtSetCells(row: ArrayBuffer[Any], protSetOpt: Option[ProteinSet]) {
    if( protSetOpt.isDefined ) row ++= protSetCellsById(protSetOpt.get.id)
    else row ++= Array.fill(proSetHeaders.length)("")
  }
  
  def appendPepInstCells(row: ArrayBuffer[Any], pepInstOpt: Option[PeptideInstance]) {
    if( pepInstOpt.isDefined ) row ++= Array(pepInstOpt.get.peptide.sequence)
    else row ++= Array.fill(pepHeaders.length)("")
  }
  
  def appendProtSetAndPepInstCells(row: ArrayBuffer[Any], protSetOpt: Option[ProteinSet], pepInstOpt: Option[PeptideInstance]) {
    appendProtSetCells(row,protSetOpt)
    appendPepInstCells(row,pepInstOpt)
  }
  
  def mkRowHeader( quantChannelCount: Int ): String = {
    val rowHeaders = new ArrayBuffer[String] ++ proSetHeaders ++ pepHeaders ++ mqPepHeaders
    for( i <- 1 to quantChannelCount ) rowHeaders ++= ( qPepHeaders.map(_+"_"+i) )
    rowHeaders.mkString("\t")
  }

}