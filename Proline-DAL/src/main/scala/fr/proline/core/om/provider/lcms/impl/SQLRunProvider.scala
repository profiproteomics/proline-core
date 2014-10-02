package fr.proline.core.om.provider.lcms.impl

import fr.profi.jdbc.ResultSetRow
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.StringUtils
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.{SelectQueryBuilder1, SelectQueryBuilder2}
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.uds.{UdsDbInstrumentTable, UdsDbRawFileTable, UdsDbRunTable}
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.core.om.provider.lcms.IScanSequenceProvider

class SQLRawFileProvider(val udsDbCtx: DatabaseConnectionContext) {
  
  protected val RawFileCols = UdsDbRawFileTable.columns
  protected val InstCols = UdsDbInstrumentTable.columns
  
  def getRawFile( rawFileName: String): Option[RawFile] = {
    getRawFiles( Seq(rawFileName) ).headOption
  }

  def getRawFiles( rawFileNames: Seq[String] ): Array[RawFile] = {
    if( rawFileNames.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { ezDBC =>
      
      val rawFileQuery = new SelectQueryBuilder2(UdsDbRawFileTable, UdsDbInstrumentTable).mkSelectQuery( (t1,c1, t2, c2) =>
        List(t1.*, t2.*) -> 
          " WHERE "~ t1.NAME ~" IN "~ rawFileNames.mkString("('","','","')") ~
          " AND "~ t1.INSTRUMENT_ID ~ "="~ t2.ID
      )
      
      ezDBC.select( rawFileQuery ) { rawFileRecord =>
        
        val rawFilePropsStr = rawFileRecord.getStringOption(RawFileCols.SERIALIZED_PROPERTIES.toAliasedString)
        require( rawFilePropsStr.isDefined && StringUtils.isNotEmpty(rawFilePropsStr.get), "Can't fetch raw file serialized properties")
        
        val rawFileProperties = ProfiJson.deserialize[RawFileProperties](rawFilePropsStr.get)
        require(rawFileProperties != null, "RawFileProperties is null, json : " + rawFilePropsStr.get) 
        require(rawFileProperties.getMzdbFilePath != null, "Can't fetch mzDbFilePath from rawFileProperties")
        
        // TODO: cache already loaded instruments
        new RawFile(
          name = rawFileRecord.getString(RawFileCols.NAME.toAliasedString),
          extension = rawFileRecord.getString(RawFileCols.EXTENSION),
          directory = rawFileRecord.getStringOption(RawFileCols.DIRECTORY),
          creationTimestamp = rawFileRecord.getDateOption(RawFileCols.CREATION_TIMESTAMP),
          instrument = Some(
            new Instrument(
              rawFileRecord.getInt(InstCols.ID),
              rawFileRecord.getString(InstCols.NAME.toAliasedString),
              rawFileRecord.getString(InstCols.SOURCE)
            )
          ),
          properties = Some(rawFileProperties)
        )
        
      } toArray
    })
    
  }
  
}

class SQLRunProvider(
  val udsDbCtx: DatabaseConnectionContext,
  val scanSeqProvider: Option[IScanSequenceProvider] = None
) extends IRunProvider {
  
  protected val rawFileProvider = new SQLRawFileProvider(udsDbCtx)
  
  protected val InstCols = UdsDbInstrumentTable.columns
  protected val RawFileCols = UdsDbRawFileTable.columns
  protected val RunCols = UdsDbRunTable.columns
  
  def getRuns( runIds: Seq[Long] ): Array[LcMsRun] = {
    if( runIds.isEmpty ) return Array()
    
    val scanSeqByIdAsOpt = if( scanSeqProvider.isEmpty ) None
    else {
      val scanSeqs = scanSeqProvider.get.getScanSequences(runIds)
      Some( Map() ++ scanSeqs.map( scanSeq => scanSeq.runId -> scanSeq ) )
    }
    
    val runs = new Array[LcMsRun](runIds.length)
    var runIdx = 0
    
    // Load runs
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { ezDBC =>
      
      val runQuery = new SelectQueryBuilder1(UdsDbRunTable).mkSelectQuery( (t1,c1) =>
        List(t1.*) -> "WHERE "~ t1.ID ~" IN ("~ runIds.mkString(",") ~") "
      )
      
      val runRecords = ezDBC.selectAllRecords(runQuery)
      val rawFileNames = runRecords.map( _.getString(RunCols.RAW_FILE_NAME) )
      val rawFiles = rawFileProvider.getRawFiles(rawFileNames)
      val rawFileByName = rawFiles.map( raw => raw.name -> raw ).toMap
      
      for( runRecord <- runRecords ) {
        
        val rawFile = rawFileByName( runRecord.getString(RunCols.RAW_FILE_NAME) )
        val runScanSeq = scanSeqByIdAsOpt.map( _(toLong(runRecord.getAny(RunCols.ID))) )
        
        // Build the run
        runs(runIdx) = this.buildRun(runRecord, rawFile, runScanSeq)
        
        runIdx += 1
      }
      
      runs
      
    })
    
  }
  
  
  def buildRun( runRecord: IValueContainer, rawFile: RawFile, scanSeq: Option[LcMsScanSequence] ): LcMsRun = {
    
    // Parse properties
    val runPropsStr = runRecord.getStringOption(RunCols.SERIALIZED_PROPERTIES)
  
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
      scanSequence = scanSeq,
      properties = runPropsStr.map( ProfiJson.deserialize[LcMsRunProperties](_) )
    )
  }
  

}
