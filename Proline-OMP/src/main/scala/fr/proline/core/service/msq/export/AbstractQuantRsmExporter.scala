package fr.proline.core.service.msq.export

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import scala.Array.canBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import fr.profi.util.primitives.toLong
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder.any2ClauseAdd
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.uds.UdsDbQuantChannelTable
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msq.ComputedRatio
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.dal.tables.msi.MsiDbResultSummaryTable
import org.joda.convert.ToString
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.msi.MsiDbMsiSearchTable

abstract class AbstractQuantRsmExporter extends IService {
  
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
  protected val statHeaders = "ratio t-test_pvalue z-test_pvalue".split(" ")
  
  protected val quantRsmId = udsDbHelper.getMasterQuantChannelQuantRsmId( masterQuantChannelId )
  protected val qcIds = udsDbHelper.getQuantChannelIds(masterQuantChannelId)
  

  
  lazy val nameByQchId : Map[Long,String] = {
    
	val qChIdByRsmId : Map[Long, Long]= { DoJDBCReturningWork.withEzDBC(execCtx.getUDSDbConnectionContext(), { ezDBC =>
   
	 	val rsmIdForquantChannelQuery = new SelectQueryBuilder1(UdsDbQuantChannelTable).mkSelectQuery(
		  (t1, c1) => List( t1.IDENT_RESULT_SUMMARY_ID, t1.ID) ->
		  " WHERE " ~ t1.ID ~ " IN(" ~ qcIds.mkString(",") ~ ")"          
		)
  
		ezDBC.select(rsmIdForquantChannelQuery){ r =>
        	toLong(r.nextAny) -> toLong(r.nextAny)
	 	} toMap
       
	 })  
	}
	
  DoJDBCReturningWork.withEzDBC(execCtx.getMSIDbConnectionContext(), { ezDBC =>
              
       val rsNameForRsmIdQuery = new SelectQueryBuilder3(MsiDbResultSetTable,MsiDbResultSummaryTable,MsiDbMsiSearchTable).mkSelectQuery(
        (t1,c1,t2,c2, t3, c3) =>List(t2.ID, t3.RESULT_FILE_NAME) ->
          " WHERE " ~ t2.ID ~ " IN(" ~ qChIdByRsmId.keys.mkString(",") ~ ") " ~
          "AND " ~ t1.ID ~ "=" ~ t2.RESULT_SET_ID ~ " AND " ~ t3.ID ~" = "~ t1.MSI_SEARCH_ID 
      )
  
      val resultBuilder = Map.newBuilder[Long,String]
      ezDBC.selectAndProcess( rsNameForRsmIdQuery ) { r => 
        
        resultBuilder += qChIdByRsmId(toLong(r.nextAny)) -> r.nextString     
        ()
      }
       
       resultBuilder.result
       
    })
  }

  protected val quantRSM = {
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      execCtx.getMSIDbConnectionContext,
      execCtx.getPSDbConnectionContext,
      execCtx.getUDSDbConnectionContext
    )
    quantRsmProvider.getQuantResultSummary(quantRsmId.get, qcIds, true).get
  }
  
  protected val protMatchById = quantRSM.resultSummary.resultSet.get.getProteinMatchById
  
  protected val protSetCellsById = {
    
    val tmpProtSetCellsById = new HashMap[Long,ArrayBuffer[Any]]
    for( mqProtSet <- quantRSM.masterQuantProteinSets ) {
      val protMatch = protMatchById( mqProtSet.proteinSet.getSameSetProteinMatchIds(0) )
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
  
  private def _getRatioStats(r: ComputedRatio) = Array(r.getState, r.getTTestPValue.getOrElse(""), r.getZTestPValue.getOrElse(""))
  protected def stringifyRatiosStats(ratios: List[Option[ComputedRatio]]): List[String] = {
    ratios.flatMap(_.map( this._getRatioStats(_).map(_.toString) ).getOrElse(Array.fill(3)("")) )
  }

}