package fr.proline.core.dbunit

import java.io.File
import java.io.InputStream
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap
import fr.profi.util.collection._
import fr.profi.util.dbunit._
import fr.profi.util.primitives._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.ps._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.builder._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.dal.tables.pdi.PdiDb
import fr.proline.core.dal.tables.lcms.LcmsDb
import fr.proline.repository.ProlineDatabaseType

/**
 * @author David Bouyssie
 *
 */
class DbUnitResultFile(
  msiDatasetInputStream: InputStream,
  udsDatasetInputStream: InputStream,
  psDatasetInputStream: InputStream
) extends IResultFile {
  
  private val RSCols = MsiDbResultSetColumns
  
  private val psDbDsParser = new PsDbDatasetParser( psDatasetInputStream )
  
  private val msiDbDsParser = new MsiDbDatasetParser(
    msiDatasetInputStream,
    udsDatasetInputStream,
    psDbDsParser
  )
  
  val fileLocation: File = new File("/dev/null")
  val importProperties: Map[String, Any] = Map()
  
  val msLevel: Int = if( msiDbDsParser.msiSearch.searchSettings.pmfSearchSettings.isDefined ) 1 else 2
  val msiSearch: MSISearch = msiDbDsParser.msiSearch
  val msQueries: Array[MsQuery] = msiDbDsParser.msQueries
  val hasDecoyResultSet: Boolean = msiDbDsParser.getResultSetRecords().find( _(RSCols.TYPE) == "DECOY_SEARCH" ).isDefined
  val hasMs2Peaklist: Boolean = ( msLevel == 2 )
  
  this.instrumentConfig = Option(msiSearch.searchSettings.instrumentConfig)
  this.peaklistSoftware = Option(msiSearch.peakList.peaklistSoftware)
  
  def getResultSet( wantDecoy: Boolean ): ResultSet = {
    
    if( wantDecoy )
      msiDbDsParser.resultSets.find( _.isDecoy ).get
    else
      msiDbDsParser.resultSets.find( _.isDecoy == false ).get    
    
  }
  
  def eachSpectrumMatch( wantDecoy: Boolean, onEachSpectrumMatch: SpectrumMatch => Unit ) {
    // TODO: parse spectrum matches
  }
  
  def eachSpectrum( onEachSpectrum: Spectrum => Unit ) {
    for( spectrum <- msiDbDsParser.getSpectra(this.instrumentConfig.get.id) ) {
      onEachSpectrum(spectrum)
    }
  }
  
  def close(): Unit = {}

}

object DbUnitDatasetParser {
  
  def parseAndFixDataset( datasetInputStream: InputStream ): Map[String,Seq[StringMap]] = {
    
    val dataset = parseDbUnitDataset( datasetInputStream, lowerCase = true ).toMap
    
    // Change the sign of all IDs into negative values
    reverseRecordsId( dataset )
    
    dataset
  }
  
  def castRecords( rawRecords: Array[Map[String, String]], colNames: Seq[String] ): Array[Map[String, Any]] = {
    rawRecords.map { rawRecord =>
      for( (k,v) <- rawRecord ) yield ( k -> parseString(v) )
    }
  }
  
  def castRecords( rawRecords: Array[StringMap], colNames: Seq[String] ): Array[AnyMap] = {
    rawRecords.map { rawRecord =>
      val record = new AnyMap()
      
      for( colName <- colNames ) {
        rawRecord.get(colName) match {
          case Some(v) => record(colName) = parseString( rawRecord(colName) )
          case None => record(colName) = null
        }
      }
        
      record
    }
  }
  
  def reverseRecordsId(recordsByTableName: Map[String, Seq[StringMap]]) {
    for ((tableName, records) <- recordsByTableName) {
      for (record <- records) {
        for ((k, v) <- record) {
          if (k == "id" || k.endsWith("_id")) record(k) = ("-" + v)
        }
      }
    }
  }
  
  /*def reverseRecordsId( recordsByTableName: Map[String,Seq[StringMap]] ) {
    for( (tableName,records) <- recordsByTableName ) {
      tableName -> reverseRecordsId(records)
    }
  }
  
  def reverseRecordsId( records: Seq[StringMap] ) {
    records.map { record =>
      for( (k,v) <- record ) {
        if( k == "id" || k.endsWith("_id") ) record(k) = ("-" + v)
      }
    }
  }*/
  
  def getColNamesByTableName( dbType: ProlineDatabaseType ): Map[String,List[String]] =  {
    
    val tableInsertQueryByName = dbType match {
      case ProlineDatabaseType.LCMS => {
        for( table <- LcmsDb.tables )
          yield table.name -> table.columnsAsStrList
      }
      case ProlineDatabaseType.MSI => {
        for( table <- MsiDb.tables )
          yield table.name -> table.columnsAsStrList
      }
      case ProlineDatabaseType.PDI => {
        for( table <- PdiDb.tables )
          yield table.name -> table.columnsAsStrList
      }
      case ProlineDatabaseType.PS => {
        for( table <- PsDb.tables )
          yield table.name -> table.columnsAsStrList
      }
      case ProlineDatabaseType.UDS => {
        for( table <- UdsDb.tables )
          yield table.name -> table.columnsAsStrList
      }
      case ProlineDatabaseType.SEQ => throw new Exception("Not yet implemented !")
    }
    
    tableInsertQueryByName.toMap
  }
  
  /*def selectRecords[T]( records: Seq[IValueContainer] ): ( (IValueContainer => Unit) => Unit ) = {
    nullMapper => records.foreach _
  }
  
  def selectAndMapRecords[T]( records: Seq[IValueContainer] ): ( (IValueContainer => T) => Seq[T] ) = {
    mapper: (IValueContainer => T) => records.map( mapper(_) )
  }
  
  def mkRecordSelector( records: Seq[IValueContainer] ): Array[Long] => ( (IValueContainer => Unit) => Unit ) = {
    recordIds => { records.foreach }
  }
  
  def mkRecordMapper[T]( records: Seq[IValueContainer] ): Array[Long] => ( (IValueContainer => T) => Seq[T] ) = {
    recordIds: Array[Long] => selectAndMapRecords(records)
  }*/
  
}

class PsDbDatasetParser( datasetInputStream: InputStream ) extends IPTMProvider with IPeptideProvider {

  // Load the dataset as records
  private val psRecordByTableName = DbUnitDatasetParser.parseAndFixDataset( datasetInputStream )
  /*private val psDbColNamesByTableName = DbUnitDatasetParser.getColNamesByTableName( ProlineDatabaseType.PS )
  private val ptmColNames = psDbColNamesByTableName( PsDbPtmTable.name )
  private val ptmSpecifColNames = psDbColNamesByTableName( PsDbPtmSpecificityTable.name )
  private val pepPtmColNames = psDbColNamesByTableName( PsDbPeptidePtmTable.name )*/
  
  // Inspired by the code of the SQLPTMProvider
  // TODO: try to reduce code redundancy ???
  // Create an abstract PtmProvider and an InMemory one
  val ptmDefinitionById = {
    
    // Parse PTMs
    val ptmRawRecords = psRecordByTableName( PsDbPtmTable.name ).toArray
    val ptmRecords = ptmRawRecords//DbUnitDatasetParser.castRecords( ptmRawRecords, ptmColNames )
    val ptmRecordById = ptmRecords.map( r => r.getLong("id") -> r ).toMap

    // Parse PTM evidences
    val ptmEvidenceRecords = psRecordByTableName( PsDbPtmEvidenceTable.name ).toArray

    // Group PTM evidences by PTM id
    val ptmEvidRecordsByPtmId = ptmEvidenceRecords.groupBy( r => toLong(r(PsDbPtmEvidenceColumns.PTM_ID)) )
    
    // Parse PTM specificities 
    /*val ptmSpecifRecords = DbUnitDatasetParser.castRecords( 
      psRecordByTableName( PsDbPtmSpecificityTable.name ).toArray,
      ptmSpecifColNames
    )*/
    val ptmSpecifRecords = psRecordByTableName( PsDbPtmSpecificityTable.name ).toArray
    
    // Build PTM definitions
    val ptmDefLongMap = new LongMap[PtmDefinition]()
    
    for( ptmSpecifRecord <- ptmSpecifRecords ) {

      // Retrieve corresponding PTM
      val ptmId = toLong(ptmSpecifRecord(PsDbPtmSpecificityColumns.PTM_ID))
      val ptmRecord = ptmRecordById(ptmId)

      // Retrieve corresponding PTM evidences
      val ptmEvidRecords = ptmEvidRecordsByPtmId.get(ptmId).get

      // TODO: load classification
      // TODO: load PTM specif evidences
      val ptmDef = PtmDefinitionBuilder.buildPtmDefinition(
        ptmRecord = ptmRecord,
        ptmSpecifRecord = ptmSpecifRecord,
        ptmEvidenceRecords = ptmEvidRecords,
        ptmClassification = ""
      )

      ptmDefLongMap.put(ptmDef.id, ptmDef)
    }

    ptmDefLongMap
  }
  
  private def getLocatedPtmsByPepId(): LongMap[Array[LocatedPtm]] = {
    
    /*val castedPepPtmRecords = DbUnitDatasetParser.castRecords(
      psRecordByTableName.getOrElse(PsDbPeptidePtmTable.name,new ArrayBuffer()).toArray,
      pepPtmColNames
    )*/
    val pepPtmRecords = psRecordByTableName.getOrElse(PsDbPeptidePtmTable.name,new ArrayBuffer()).toArray
    val pepPtmRecordsByPepId = pepPtmRecords.toSeq.groupByLong(_.getLong("peptide_id") )
    
    PtmDefinitionBuilder.buildLocatedPtmsGroupedByPepId(pepPtmRecordsByPepId,ptmDefinitionById)
  }
  
  lazy val ptmDefByNameAndLocation: Map[Tuple3[String, Char, PtmLocation.Location], PtmDefinition] = {
    this.ptmDefinitionById.values.map { p => (p.names.shortName, p.residue, PtmLocation.withName(p.location)) -> p } toMap
  }
  
  lazy val ptmIdByName: Map[String, Long] = {
    this.ptmDefinitionById.values.map { p => p.names.shortName -> p.id } toMap
  }

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {
    val ptmDefById = this.ptmDefinitionById
    ptmDefIds.map { ptmDefById.get(_) } toArray
  }

  def getPtmDefinitions(ptmDefIds: Seq[Long]): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter(_.isDefined).map(_.get)
  }

  def getPtmDefinition(ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    this.ptmDefByNameAndLocation.get(ptmShortName, ptmResidue, ptmLocation)
  }

  def getPtmId(shortName: String): Option[Long] = {
    this.ptmIdByName.get(shortName)
  }
  
  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    var ptmToReturn: PtmDefinition = null
    this.ptmDefinitionById.values.foreach(ptm => {
      ptm.ptmEvidences.foreach(e => {
        if (scala.math.abs(ptmMonoMass - e.monoMass) <= ptmMonoMassMargin
          && ptm.residue == ptmResidue
          && ptm.location == ptmLocation.toString) {
          ptmToReturn = ptm
        }
      })
    })
    Some(ptmToReturn)
  }
  // END OF CODE DUPLICATED WITH IPTMProvider
  
  val peptides = {
    
    val locatedPtmsByPepId = this.getLocatedPtmsByPepId()
    val pepRecords = psRecordByTableName(PsDbPeptideTable.name).toArray
    
    // Iterate over peptide records to convert them into peptide objects
    val peptides = new ArrayBuffer[Peptide](pepRecords.length)

    for (pepRecord <- pepRecords) {
      val pepId = pepRecord.getLong(PsDbPeptideColumns.ID)

      val locatedPtmsOpt = locatedPtmsByPepId.get(pepId)
      peptides += PeptideBuilder.buildPeptide(pepRecord, locatedPtmsOpt)
    }

    peptides.toArray
  }
  
  val peptideById = peptides.map( p => p.id -> p).toMap
  
  def getPeptidesAsOptions( peptideIds: Seq[Long] ): Array[Option[Peptide]] = {
    peptideIds.toArray.map(peptideById.get(_))
  }
  
  def getPeptides( peptideIds: Seq[Long] ): Array[Peptide] = {
    getPeptidesAsOptions(peptideIds).withFilter( _.isDefined ).map( _.get )
  }
  
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm] ): Option[Peptide] = null
  
  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]] ): Array[Option[Peptide]] = null
  
}

class MsiDbDatasetParser(
  msiDatasetInputStream: InputStream,
  udsDatasetInputStream: InputStream,
  psDbDatasetParser: PsDbDatasetParser
) {
  
  import DbUnitDatasetParser._

  // --- BEGIN OF CONSTRUCTOR ---
  
  // Load the dataset as records
  val msiRecordByTableName = DbUnitDatasetParser.parseAndFixDataset( msiDatasetInputStream )
  private val udsRecordByTableName = DbUnitDatasetParser.parseAndFixDataset( udsDatasetInputStream )
  val scoreTypeById: Map[Long,String] = {
    val ScoringCols = MsiDbScoringColumns
    val scoringRecords = msiRecordByTableName( MsiDbScoringTable.name )
    
    scoringRecords.map { r =>
      val scoreType = r(ScoringCols.SEARCH_ENGINE) + ':' + r(ScoringCols.NAME)
      r.getLong(ScoringCols.ID) -> scoreType
    } toMap
  }
  
  val msiSearch = parseMsiSearch()
  val msQueries = parseMsQueries()
  val peptideMatches = parsePeptideMatches()
  val proteinMatches = parseProteinMatches()
  val resultSets = parseResultSets()
  
  def getSpectra( instrumentConfigId: Long ) = parseSpectra( instrumentConfigId: Long )
  
  // --- END OF CONSTRUCTOR ---
  
  def getResultSetRecords(): Array[StringMap] = {
    msiRecordByTableName(MsiDbResultSetTable.name).toArray
  }
  
  private def parseMsiSearch(): MSISearch = {
    
    // Map fragmentation series by the instrument config id
    // FIXME: only FragmentIonRequirement rules are considered in the InstrumentConfigBuilder => this should be fixed
    val fragSeriesById = udsRecordByTableName( UdsDbFragmentationSeriesTable.name ).mapByLong(_.getLong("id"))
    val fragSeriesIdByFragRuleId = udsRecordByTableName(
      UdsDbFragmentationRuleTable.name
    ).withFilter(_.contains("fragment_series_id")).toLongMapWith { fragRule =>
      fragRule.getLong("id") -> fragRule.getLong("fragment_series_id")
    }
    
    val fragSeriesByInstConfigId = new LongMap[ArrayBuffer[StringMap]]()
    udsRecordByTableName( UdsDbInstrumentConfigFragmentationRuleMapTable.name ).foreach { mapping =>
      val instConfigId = mapping.getLong("instrument_config_id")
      val fragRuleId = mapping.getLong("fragmentation_rule_id")
      val fragSeriesIdOpt = fragSeriesIdByFragRuleId.get(fragRuleId)
      
      if (fragSeriesIdOpt.isDefined) {
        val fragSeriesId = fragSeriesIdOpt.get
        val fragSeries = fragSeriesById(fragSeriesId)
        fragSeriesByInstConfigId.getOrElseUpdate(instConfigId, new ArrayBuffer[StringMap]) += fragSeries
      }
      
    }
    
    // TODO: use LongMap instead of HashMap
    val tmpMsiSearch = MsiSearchBuilder.buildMsiSearches(
      msiRecordByTableName( MsiDbMsiSearchTable.name ),
      msiRecordByTableName( MsiDbPeaklistTable.name ).map( p => p.getLong("id") -> p ).toMap,
      msiRecordByTableName( MsiDbPeaklistSoftwareTable.name ).map( p => p.getLong("id") -> p ).toMap,
      msiRecordByTableName( MsiDbSearchSettingsTable.name ).map( p => p.getLong("id") -> p ).toMap,
      msiRecordByTableName.getOrElse( MsiDbIonSearchTable.name,new ArrayBuffer() ).map( p => p.getLong("id") -> p ).toMap,
      msiRecordByTableName.getOrElse( MsiDbMsmsSearchTable.name,new ArrayBuffer() ).map( p => p.getLong("id") -> p ).toMap,
      // FIXME: use the mapping between tables to fetch MsiDbSeqDatabaseTable records
      msiRecordByTableName( MsiDbSeqDatabaseTable.name ).toSeq.groupBy( _.getLong("id") ),
      msiRecordByTableName( MsiDbUsedEnzymeTable.name ).toSeq.groupBy( _.getLong("search_settings_id") ),
      msiRecordByTableName.getOrElse( MsiDbUsedPtmTable.name,new ArrayBuffer() ).toSeq.groupBy( _.getLong("search_settings_id") ),
      udsRecordByTableName( UdsDbEnzymeTable.name ).map( p => p.getLong("id") -> p ).toMap,
      udsRecordByTableName( UdsDbEnzymeCleavageTable.name ).map( p => p.getLong("id") -> p ).toMap,
      udsRecordByTableName( UdsDbInstrumentConfigTable.name ).map( p => p.getLong("id") -> p ).toMap,
      udsRecordByTableName( UdsDbInstrumentTable.name ).map( p => p.getLong("id") -> p ).toMap,
      fragSeriesByInstConfigId.toMap,
      psDbDatasetParser
    ).head
    
    tmpMsiSearch
  }
  
  // Inspired from SQLMsQueryProvider
  // TODO: remove code redundancy
  private def parseMsQueries(): Array[MsQuery] = {
    
    val MsQueryCols = MsiDbMsQueryColumns
    val SpectrumCols = MsiDbSpectrumColumns
    
    val msQueryRecords = msiRecordByTableName(MsiDbMsQueryTable.name).toArray
    val spectrumRecords = msiRecordByTableName(MsiDbSpectrumTable.name)
    val spectrumTitleById = spectrumRecords.map( s => s.getLong(SpectrumCols.ID) -> s(SpectrumCols.TITLE) ).toMap

    // Load MS queries corresponding to the provided MSI search ids
    for( msQueryRecord <- msQueryRecords ) yield {
      
      val spectrumId = toLong(msQueryRecord(MsQueryCols.SPECTRUM_ID))
      
      // Decode JSON properties
      val properties = msQueryRecord.get(MsQueryCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[MsQueryProperties](_))
      val msQueryId = msQueryRecord.getLong(MsQueryCols.ID)

      // Build the MS query object
      val msQuery = if (spectrumId != 0) { // we can assume it is a MS2 query
        MsQueryBuilder.buildMs2Query(msQueryRecord,spectrumTitleById)
      } else {
        MsQueryBuilder.buildMs1Query(msQueryRecord)
      }

      msQuery
    }

  }
  
  private def parsePeptideMatches(): Array[PeptideMatch] = {
    val pepMatchRecords = msiRecordByTableName(MsiDbPeptideMatchTable.name).toArray
    
    PeptideMatchBuilder.buildPeptideMatches(
      pepMatchRecords,
      msQueries,
      scoreTypeById,
      psDbDatasetParser
    )
  }
  
  private def parseProteinMatches(): Array[ProteinMatch] = {
    val protMatchRecords = msiRecordByTableName(MsiDbProteinMatchTable.name).toArray
    val seqMatchRecords = msiRecordByTableName(MsiDbSequenceMatchTable.name).toArray
    val seqMatchRecordsByProtMatchId = seqMatchRecords.groupBy(_.getLong(MsiDbSequenceMatchColumns.PROTEIN_MATCH_ID))
    
    for( protMatchRecord <- protMatchRecords ) yield {
      
      val protMatchId = protMatchRecord.getLong("id")
      
      ProteinMatchBuilder.buildProteinMatch(
        protMatchRecord,
        seqMatchRecordsByProtMatchId(protMatchId).map( ProteinMatchBuilder.buildSequenceMatch(_) ),
        Array(), // FIXME: retrieve the seq db ids
        scoreTypeById
      )
    }
    
  }
  
  private def parseSpectra( instrumentConfigId: Long ): Array[Spectrum] = {
    
    import org.apache.commons.codec.binary.Base64
    
    val SpecCols = MsiDbSpectrumColumns
    val spectrumRecords = msiRecordByTableName(MsiDbSpectrumTable.name).toArray
    
    for( spectrumRecord <- spectrumRecords ) yield {
      
      val newSpectrumRecord = new AnyMap()
      newSpectrumRecord ++= spectrumRecord
      newSpectrumRecord(SpecCols.MOZ_LIST) = Base64.decodeBase64( spectrumRecord(SpecCols.MOZ_LIST) )
      newSpectrumRecord(SpecCols.INTENSITY_LIST) = Base64.decodeBase64( spectrumRecord(SpecCols.INTENSITY_LIST) )
      newSpectrumRecord(SpecCols.INSTRUMENT_CONFIG_ID) = instrumentConfigId
      
      SpectrumBuilder.buildSpectrum( newSpectrumRecord )
    }
    
  }
  
  private def parseResultSets(): Array[ResultSet] = {
    
    val RSCols = MsiDbResultSetColumns
    val rsRecords = msiRecordByTableName(MsiDbResultSetTable.name).toArray
    val isValidatedContent = false
    
    // Build some mappings
    val pepMatchesByRsId = this.peptideMatches.groupBy(_.resultSetId)
    val protMatchesByRsId = this.proteinMatches.groupBy(_.resultSetId)
    
    for( rsRecord <- rsRecords ) yield {
      
      ResultSetBuilder.buildResultSet(
        rsRecord,
        isValidatedContent,
        Map( msiSearch.id -> msiSearch ),
        Map(),
        protMatchesByRsId,
        pepMatchesByRsId
      )
    }
    
  }
  
}

