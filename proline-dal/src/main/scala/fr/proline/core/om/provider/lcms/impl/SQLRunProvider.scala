package fr.proline.core.om.provider.lcms.impl

import fr.profi.util.primitives._
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.uds.UdsDbInstrumentTable
import fr.proline.core.dal.tables.uds.UdsDbRawFileTable
import fr.proline.core.dal.tables.uds.UdsDbRunTable
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.provider.IProlinePathConverter
import fr.proline.core.om.provider.ProlineManagedDirectoryType
import fr.proline.core.om.provider.lcms.IRunProvider
import fr.proline.core.om.provider.lcms.IScanSequenceProvider

class SQLRawFileProvider(val udsDbCtx: UdsDbConnectionContext, val pathConverter: Option[IProlinePathConverter] ) {
  
  protected val RawFileCols = UdsDbRawFileTable.columns
  protected val InstCols = UdsDbInstrumentTable.columns
  
  def getRawFile( rawFileIdentifier: String): Option[RawFile] = {
    getRawFiles( Seq(rawFileIdentifier) ).headOption
  }

  def getRawFiles( rawFileIdentifiers: Seq[String] ): Array[RawFile] = {
    if( rawFileIdentifiers.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      
      val rawFileQuery = new SelectQueryBuilder1(UdsDbRawFileTable).mkSelectQuery( (t1,c1) =>
        List(t1.*) -> 
          " WHERE "~ t1.IDENTIFIER ~" IN "~ rawFileIdentifiers.mkString("('","','","')")
      )
      
      ezDBC.select( rawFileQuery ) { rawFileRecord =>
        
        val rawFilePropsStrOpt = rawFileRecord.getStringOption(RawFileCols.SERIALIZED_PROPERTIES)
        val rawFileProperties = rawFilePropsStrOpt.map( ProfiJson.deserialize[RawFileProperties](_) )
        
        val directory = rawFileRecord.getStringOption(RawFileCols.RAW_FILE_DIRECTORY).map { str =>
          if( pathConverter.isEmpty ) str
          else pathConverter.get.prolinePathToAbsolutePath(str, ProlineManagedDirectoryType.RAW_FILES)
        }
        val mzdbFileDirectory = rawFileRecord.getStringOption(RawFileCols.MZDB_FILE_DIRECTORY).map { str =>
          if( pathConverter.isEmpty ) str
          else pathConverter.get.prolinePathToAbsolutePath(str, ProlineManagedDirectoryType.MZDB_FILES)
        }
        
        new RawFile(
          identifier = rawFileRecord.getString(RawFileCols.IDENTIFIER),
          name = rawFileRecord.getString(RawFileCols.RAW_FILE_NAME),
          directory = directory,
          mzdbFileName = rawFileRecord.getStringOption(RawFileCols.MZDB_FILE_NAME),
          mzdbFileDirectory = mzdbFileDirectory,
          sampleName = rawFileRecord.getStringOption(RawFileCols.SAMPLE_NAME),
          creationTimestamp = rawFileRecord.getDateOption(RawFileCols.CREATION_TIMESTAMP),
          properties = rawFileProperties
        )
        
      } toArray
    }
    
  }
  
}

class SQLRunProvider(
  val udsDbCtx: UdsDbConnectionContext,
  val scanSeqProvider: Option[IScanSequenceProvider],
  val pathConverter: Option[IProlinePathConverter] = None
) extends IRunProvider {
  
  protected val rawFileProvider = new SQLRawFileProvider(udsDbCtx, pathConverter)
  
  protected val InstCols = UdsDbInstrumentTable.columns
  protected val RawFileCols = UdsDbRawFileTable.columns
  protected val RunCols = UdsDbRunTable.columns
  
  def getRuns( runIds: Seq[Long], loadScanSequence: Boolean = false ): Array[LcMsRun] = {
    if( runIds.isEmpty ) return Array()
    
    // Remove duplicated run ids
    val distinctRunIds = runIds.distinct
    
    val scanSeqByIdAsOpt = if( !loadScanSequence ) None
    else {
      require( scanSeqProvider.isDefined, "a scan sequence provider must be defined")
      val scanSeqs = scanSeqProvider.get.getScanSequences(runIds)
      Some( Map() ++ scanSeqs.map( scanSeq => scanSeq.runId -> scanSeq ) )
    }


    val runs = new Array[LcMsRun](distinctRunIds.length)
    val runIdxById = distinctRunIds.zipWithIndex.toLongMap

    // Load runs
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { ezDBC =>
      
      val runQuery = new SelectQueryBuilder1(UdsDbRunTable).mkSelectQuery( (t1,c1) =>
        List(t1.*) -> "WHERE "~ t1.ID ~" IN ("~ distinctRunIds.mkString(",") ~") "
      )
      
      val runRecords = ezDBC.selectAllRecords(runQuery)
      val rawFileIdentifiers = runRecords.map( _.getString(RunCols.RAW_FILE_IDENTIFIER) )
      val rawFiles = rawFileProvider.getRawFiles(rawFileIdentifiers)
      val rawFileByIdentifier = rawFiles.map( raw => raw.identifier -> raw ).toMap
      
      for( runRecord <- runRecords ) {
        
        val rawFile = rawFileByIdentifier( runRecord.getString(RunCols.RAW_FILE_IDENTIFIER) )
        val runScanSeq = scanSeqByIdAsOpt.map( _(runRecord.getLong(RunCols.ID)) )
        
        // Build the run
        val run = this.buildRun(runRecord, rawFile, runScanSeq)
        runs(runIdxById(run.id)) = run
      }
      runs
    }
    
  }
  
  
  def buildRun( runRecord: IValueContainer, rawFile: RawFile, scanSeq: Option[LcMsScanSequence] ): LcMsRun = {
    
    // Parse properties
    val runPropsStr = runRecord.getStringOption(RunCols.SERIALIZED_PROPERTIES)
  
    new LcMsRun(
      id = runRecord.getLong(RunCols.ID),
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
