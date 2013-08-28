package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.context.IExecutionContext
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.api.service.IService

trait XQuantRsmExporter extends IService {
  
  // Define the interface
  val execCtx: IExecutionContext
  val masterQuantChannelId: Long
  val outputFile: File
  
  protected def mkRowHeader( quantChannelCount: Int ): String
  protected def writeRows( fileWriter: PrintWriter )
  
  // Defines some values
  protected val udsDbHelper = new UdsDbHelper(execCtx.getUDSDbConnectionContext())
  protected val locale = java.util.Locale.ENGLISH  

  protected val protSetHeaders = "AC description selection_level".split(" ")
  
  protected val quantRsmId = udsDbHelper.getMasterQuantChannelQuantRsmId( masterQuantChannelId )
  protected val qcIds = udsDbHelper.getQuantChannelIds(masterQuantChannelId)

  protected val quantRSM = {
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      execCtx.getMSIDbConnectionContext,
      execCtx.getPSDbConnectionContext,
      execCtx.getUDSDbConnectionContext
    )
    quantRsmProvider.getQuantResultSummary(quantRsmId.get, qcIds, true).get
  }
  
  protected val protMatchById = quantRSM.resultSummary.resultSet.get.proteinMatchById
  
  protected val protSetCellsById = {
    
    val tmpProtSetCellsById = new HashMap[Long,ArrayBuffer[Any]]
    for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
      val protMatch = protMatchById( mqProtSet.proteinSet.proteinMatchIds(0) )
      val protSetCells = new ArrayBuffer[Any]
      protSetCells += protMatch.accession
      protSetCells += protMatch.description
      protSetCells += mqProtSet.selectionLevel
      
      tmpProtSetCellsById += mqProtSet.id -> protSetCells
    }
    
    tmpProtSetCellsById
  }

  def runService() = {
          
    // Create a file writer and print the header
    val fileWriter = new PrintWriter(new FileOutputStream(outputFile))
    fileWriter.println(mkRowHeader(qcIds.length))
    fileWriter.flush()
    
    writeRows( fileWriter )
    
    fileWriter.close()
    
    true
  }
  
  protected def appendProtSetCells(row: ArrayBuffer[Any], protSetOpt: Option[ProteinSet]) {
    if( protSetOpt.isDefined ) row ++= protSetCellsById(protSetOpt.get.id)
    else row ++= Array.fill(protSetHeaders.length)("")
  }

}