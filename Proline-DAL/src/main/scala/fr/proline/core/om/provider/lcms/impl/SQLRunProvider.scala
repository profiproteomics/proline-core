package fr.proline.core.om.provider.lcms.impl

import scala.collection.mutable.ArrayBuffer

import fr.profi.jdbc.ResultSetRow
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.{ DoJDBCWork, DoJDBCReturningWork }
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.uds.{ UdsDbInstrumentTable, UdsDbRawFileTable, UdsDbRunTable }
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.core.om.provider.lcms.IScanSequenceProvider
import fr.proline.util.sql._
import fr.proline.util.primitives._

class SQLRunProvider(
  val udsDbCtx: DatabaseConnectionContext,
  val scanSeqProvider: Option[IScanSequenceProvider] = None
) extends IRunProvider {
  
  val InstCols = UdsDbInstrumentTable.columns
  val RawFileCols = UdsDbRawFileTable.columns
  val RunCols = UdsDbRunTable.columns
  
  def getRuns( runIds: Seq[Long] ): Array[LcMsRun] = {
    
    val scanSeqByIdAsOpt = if( scanSeqProvider.isEmpty ) None
    else {
      val scanSeqs = scanSeqProvider.get.getScanSequences(runIds)
      Some( Map() ++ scanSeqs.map( scanSeq => scanSeq.runId -> scanSeq ) )
    }
    
    val runs = new Array[LcMsRun](runIds.length)
    var runIdx = 0
    
    // Load runs
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { ezDBC =>
      
      val runQuery = new SelectQueryBuilder3(UdsDbInstrumentTable,UdsDbRawFileTable,UdsDbRunTable).mkSelectQuery( (t1,c1,t2,c2,t3,c3) =>
        List(t1.*,t2.*,t3.*) ->
          "WHERE "~ t3.ID ~" IN("~ runIds.mkString(",") ~") "~
          "AND "~ t1.ID ~"="~ t2.INSTRUMENT_ID ~" AND "~ t2.NAME ~"="~ t3.RAW_FILE_NAME
      )
      
      ezDBC.selectAndProcess( runQuery ) { runRecord =>
        
        val runScanSeq = scanSeqByIdAsOpt.map( _(toLong(runRecord.getAny(RunCols.ID))) )
        
        // Build the run
        runs(runIdx) = this.buildRun(runRecord, runScanSeq)
        
        runIdx += 1
      }
      
      runs
      
    })
    
  }
  
  
  def buildRun( runRecord: ResultSetRow, scanSeq: Option[LcMsScanSequence] ): LcMsRun = {
    
    // TODO: parse properties
    // TODO: cache already loaded instruments
    val instrument = new Instrument(
      id = toLong( runRecord.getAny(InstCols.ID.toAliasedString) ),
      name = runRecord.getString(InstCols.NAME.toAliasedString),
      source = runRecord.getString(InstCols.SOURCE)
    )
    
    val rawFilePropsStr = runRecord.getStringOption(RawFileCols.SERIALIZED_PROPERTIES.toAliasedString)
    
    // Load the raw file
    // TODO: create a raw file provider
    val rawFile = new RawFile(
      name = runRecord.getString(RawFileCols.NAME.toAliasedString),
      extension = runRecord.getString(RawFileCols.EXTENSION),
      directory = runRecord.getStringOption(RawFileCols.DIRECTORY),
      creationTimestamp = runRecord.getDateOption(RawFileCols.CREATION_TIMESTAMP),
      instrument = Some(instrument),
      properties = rawFilePropsStr.map( ProfiJson.deserialize[RawFileProperties](_) )
    )
    
    // TODO: parse properties
    //val runPropsStr = runRecord.getStringOption(UdsDbRawFileTable.name + "_"+RunCols.SERIALIZED_PROPERTIES)
  
    new LcMsRun(
      id = toLong(runRecord.getAny(RunCols.ID.toAliasedString)),
      number = runRecord.getInt(RunCols.NUMBER),
      runStart = toFloat( runRecord.getDouble(RunCols.RUN_START) ),
      runStop = toFloat( runRecord.getDouble(RunCols.RUN_STOP) ),
      duration = toFloat( runRecord.getDouble(RunCols.DURATION) ),      
      lcMethod = runRecord.getStringOption(RunCols.LC_METHOD),
      msMethod = runRecord.getStringOption(RunCols.MS_METHOD),
      analyst = runRecord.getStringOption(RunCols.ANALYST),
      rawFile = rawFile,
      scanSequence = scanSeq
    )
  }
  

}
