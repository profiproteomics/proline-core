package fr.proline.core.om.storer.msi

import fr.proline.core._
import fr.proline.core.dal._
import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

trait IRsStorer extends Logging {
  
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  val msiDb1: MsiDb // Main MSI db connection
  lazy val msiDb2: MsiDb = new MsiDb( msiDb1.config, false, 10000 ) // Secondary MSI db connection
  
  private val psDb = new PsDb( PsDb.getDefaultConfig, false, 10000 )
  val scoringIdByType = new fr.proline.core.dal.helper.MsiDbHelper( msiDb1.config ).getScoringIdByType
  
  // TODO: implement as InMemoryProvider
  val peptideByUniqueKey = new HashMap[String,Peptide]()
  val proteinBySequence = new HashMap[String,Protein]()
  
  /**
   * Store specified new ResultSet and all associated data into dbs. 
   * Protein and peptides referenced by the result set will be created as well
   * if necessary.
   */
  def fetchExistingPeptidesIdByUniqueKey( pepSequences: Seq[String] ): Map[String,Int]
  def storeNewPeptides( peptides: Seq[Peptide] ): Array[Peptide]
  
  def fetchProteinIdentifiers( accessions: Seq[String] ): Array[Any]// TODO: use JPA
  
  def fetchExistingProteins( protCRCs: Seq[String] ): Array[Protein]
  def storeNewProteins( proteins: Seq[Protein] ): Array[Protein]
  
  def storeRsPeptideMatches( rs: ResultSet ): Int
  def storeRsProteinMatches( rs: ResultSet ): Int 
  def storeRsSequenceMatches( rs: ResultSet ): Int
  
}

/** A factory object for implementations of the IRsStorer trait */
object RsStorer {
  
  import fr.proline.core.om.storer.msi.impl.GenericRsStorer
  import fr.proline.core.om.storer.msi.impl.PgRsStorer
  import fr.proline.core.om.storer.msi.impl.SQLiteRsStorer

  def apply(msiDb: MsiDb ): RsStorer = { msiDb.config.driver match {
    case "org.postgresql.Driver" => new RsStorer( new PgRsStorer( msiDb ) )
    case "org.sqlite.JDBC" => new RsStorer( new SQLiteRsStorer( msiDb ) )
    case _ => new RsStorer( new GenericRsStorer( msiDb ) )
    }
  }
}


class RsStorer( private val _storer: IRsStorer ) extends Logging {
  
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  val msiDb1 = _storer.msiDb1
  val peptideByUniqueKey = _storer.peptideByUniqueKey
  val proteinBySequence = _storer.proteinBySequence
  
  // Only for native result sets
  def storeResultSet( resultSet: ResultSet, seqDbIdByTmpId: Map[Int,Int] ): Unit = {
    
    if( ! resultSet.isNative )
      throw new Exception("too many arguments for a non native result set")
    
    this._insertResultSet( resultSet )
    this._storeNativeResultSetObjects( resultSet, seqDbIdByTmpId )
    
    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
    
  }
  
  // Only for non native result sets
  def storeResultSet( resultSet: ResultSet ): Unit = {
    
    if( resultSet.isNative )
      throw new Exception("not enough arguments for a native result set")
    
    this._insertResultSet( resultSet )
    this._storeNonNativeResultSetObjects( resultSet )
    
    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
    
  }
  
  private def _insertResultSet( resultSet: ResultSet ): Unit = {
    
    val msiDbConn = this.msiDb1.getOrCreateConnection()
    val msiDbTx = this.msiDb1.getOrCreateTransaction()
    
    /// Define some vars
    val isDecoy = resultSet.isDecoy
    val rsName = if( resultSet.name == null ) None else Some( resultSet.name )
    val rsDesc = if( resultSet.description == null ) None else Some( resultSet.description )
    var rsType = ""
    rsType = if( resultSet.isNative ) "SEARCH" else "USER"
    rsType = if( isDecoy ) "DECOY_" + rsType else rsType
    
    val decoyRsId = if( resultSet.getDecoyResultSetId > 0 ) Some(resultSet.getDecoyResultSetId) else None
        
    // Store RDB result set
    // TODO: use JPA instead
    
    val rsInsertQuery = ResultSetTable.getInsertQuery { t =>
      List( t.name, t.description, t.`type`, t.modificationTimestamp, t.decoyResultSetId, t.msiSearchId )
    }
    
    //val rsColumns = Seq( "name","description","type","modification_timestamp","decoy_result_set_id","msi_search_id")
    //val rsColNamesAsStr = rsColumns.mkString(",")
    
    val stmt = msiDbConn.prepareStatement( rsInsertQuery, java.sql.Statement.RETURN_GENERATED_KEYS )     
    new ReusableStatement( stmt, msiDb1.config.sqlFormatter ) <<
      rsName <<
      rsDesc <<
      rsType <<
      msiDb1.stringifyDate( new java.util.Date ) <<
      decoyRsId <<
      resultSet.msiSearch.id

    stmt.execute()
    resultSet.id = this.msiDb1.extractGeneratedInt( stmt )
    
  }
  
  private def _storeNativeResultSetObjects( resultSet: ResultSet, seqDbIdByTmpId: Map[Int,Int] ): Unit = {
    
    /*val rsPeptides = resultSet.peptides
    if( rsPeptides.find( _.id < 0 ) != None )
      throw new Exception("result set peptides must first be persisted")    
    
    val rsProteins = resultSet.getProteins.getOrElse( new Array[Protein](0) )
    if( rsProteins.find( _.id < 0 ) != None )
      throw new Exception("result set proteins must first be persisted")*/
    
    // Retrieve the list of existing peptides in the current MSIdb
    val existingPeptidesIdByKey = this._storer.fetchExistingPeptidesIdByUniqueKey( resultSet.getUniquePeptideSequences )
    logger.info( existingPeptidesIdByKey.size + " existing peptides have been loaded from the database !" )
    
    val( existingPeptides, newPeptides ) = resultSet.peptides.partition( pep => existingPeptidesIdByKey.contains(pep.uniqueKey) )
    for( peptide <- existingPeptides ) {
      this.peptideByUniqueKey += ( peptide.uniqueKey -> peptide )
    }
    
    // Build a map of existing peptides
    //val peptideByUniqueKey = existingPeptides.map { pep => ( pep.uniqueKey -> pep ) } toMap
    //val newPeptides = resultSet.peptides filter { pep => ! existingPeptidesIdByKey.contains( pep.uniqueKey ) }
    
    val nbNewPeptides = newPeptides.length
    if( nbNewPeptides > 0 ) {
      logger.info( "storing "+ nbNewPeptides +" new peptides in the MSIdb...")
      val insertedPeptides = this._storer.storeNewPeptides( newPeptides )
      logger.info( insertedPeptides.length + " new peptides have been effectively stored !")
      
      for( insertedPeptide <- insertedPeptides ) {
        this.peptideByUniqueKey += ( insertedPeptide.uniqueKey -> insertedPeptide )
      }
    }
    
    // TODO: Update result set peptides
    
    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches. map { pepMatch => pepMatch.id -> pepMatch } toMap
    
    // Update MS query id of peptide matches
    this._updateRsPeptideMatches( resultSet )
    
    // Store peptide matches
    val peptideMatchCount = this._storer.storeRsPeptideMatches( resultSet )
    logger.info( "peptideMatchCount peptide matches have been stored !" )
    
    // Retrieve protein matches and their accession numbers
    val proteinMatches = resultSet.proteinMatches
    val acNumbers = proteinMatches map { _.accession }    
    
    logger.info( proteinMatches.length + " protein identifiers are going to be loaded..." )
    
    /*
    // Retrieve protein identifiers from the PDI-DB
    // TODO: implementation using JPA !
    val protIdents = this.fetchProteinIdentifiers( acNumbers )
    
    // Retrieve CRC64 of protein identifiers
    val protCrcs = protIdents map { _.bioSequence.crc64 } 
    
    // Retrieve existing proteins in the MSI-DB
    val rdbExistingProteins = this.fetchExistingRdbProteins( protCrcs )
    
    // Map existing proteins by their sequence
    val rdbProteinBySeq = map { _.sequence = _ } rdbExistingProteins
    
    ////// Map protein identifiers by the the identifier value
    val( protIdentsByAc, newProteinByCrc64, taxonIdMap )
    for( rdbProtIdent <- protIdents ) {
      push( @{ protIdentsByAc(rdbProtIdent.value) }, rdbProtIdent )
      
      ////// Check if protein already exists in the database
      val protein = rdbProtIdent.bioSequence
      newProteinByCrc64(protein.crc64) = protein if ! exists rdbProteinBySeq( protein.sequence )
      
      taxonIdMap(rdbProtIdent.taxonId) = 1
    }
    print scalar( keys(protIdentsByAc) ) . " retrieved protein identifiers\n"
    
    val newProteins = values(newProteinByCrc64)
    
    val nbNewProteins = scalar( newProteins )
    
    if( nbNewProteins > 0 ) {
      print "storing nbNewProteins new proteins...\n"
      
      ////// Store proteins which are ! present in the MSI-DB
      val newProtCount = this.storeNewProteins( newProteins, rdbProteinBySeq )    
      print "newProtCount new proteins have been effectively stored !\n"
      
      // Register a copy of the protein with its new id
      //this.proteinBySequence += protein.sequence -> protein.copy( id = this.msiDb1.extractGeneratedInt( stmt ) )
    }
    
    ////// Retrieve PDI protein ids and taxon ids
    val pdiProteinIds = map { _.bioSequenceId } protIdents
    //val taxonIds = keys(activeTaxonIdMap)
    
    ////// Retrieve protein names of the newly inserted proteins
    // TODO: instantiate a PdiDb helper to do this
    val protNameByTaxonAndId = this.fetchProteinNameByTaxonAndId( pdiProteinIds, taxonIdMap )
    
    ////// Update protein matches
    val unknownProteins = this._updateRsProteinMatches( resultSet, rdbProteinBySeq, protIdentsByAc,
                                                              protNameByTaxonAndId, seqDbIdByTmpId )
    print scalar(unknownProteins) ." protein identifiers weren't mapped in the database\n"
    */
    
    this._updateRsProteinMatches( resultSet, null, null, null, seqDbIdByTmpId )
    
    // Store protein matches
    this._storer.storeRsProteinMatches( resultSet )
    logger.info( "protein matches have been stored" )
    
    // Update sequence matches
    this._updateRsSequenceMatches( resultSet, peptideMatchByTmpId )
    
    // Store sequence matches
    this._storer.storeRsSequenceMatches( resultSet )
    logger.info( "sequence matches have been stored" )
    
  }
  
  private def _storeNonNativeResultSetObjects( resultSet: ResultSet ): Unit = {
    
    //resultSet.updateRsIdOfAllObjects() ////// is it still needed ?
    
    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches map { pepMatch => pepMatch.id -> pepMatch } toMap
    
    // Store peptide matches
    val peptideMatchCount = this._storer.storeRsPeptideMatches( resultSet )    
    logger.info( "peptideMatchCount peptide matches have been stored !" )
    
    // Store protein matches
    this._storer.storeRsProteinMatches( resultSet )    
    logger.info( "protein matches have been stored" )
    
    // Update sequence matches (replace peptide match tmp ids by database ids)
    this._updateRsSequenceMatches( resultSet, peptideMatchByTmpId )
    
    // Store sequence matches
    this._storer.storeRsSequenceMatches( resultSet )
    logger.info( "sequence matches have been stored" )
    
  }
  
  private def _updateRsPeptideMatches( resultSet: ResultSet ): Unit = {
    
    // msQueryIdByTmpId: Map[Int,Int]
    
    // Retrieve some vars
    val rsId = resultSet.id
    
    // Iterate over peptide matches to update their result set id
    resultSet.peptideMatches.foreach { _.resultSetId = rsId  }
    
  }
  
  private def _updateRsProteinMatches( resultSet: ResultSet,
                                      rdbProtBySeq: Map[String,Any],
                                      protIdentsByAc: Map[String,Any],
                                      protNameByTaxonAndId: Map[String,Any],
                                      seqDbIdByTmpId: Map[Int,Int] ): Array[String] = {
    
    // Retrieve some vars
    val rsId = resultSet.id
    val proteinMatches = resultSet.proteinMatches
        
    // Iterate over protein matches
    val unmappedAccessions = new ArrayBuffer[String](0)
    for( proteinMatch <- proteinMatches ) {
      
      proteinMatch.seqDatabaseIds = proteinMatch.seqDatabaseIds .map { seqDbIdByTmpId.get(_).getOrElse(0) }
                                                                .filter { _ != 0 }
      proteinMatch.resultSetId = rsId
      
      // TODO: fix this when JPA is ok
      /*
      ////// Check if ac exists
      val ac = proteinMatch.accession
      die "_update_rs_protein_matches: protein match hasn't an accession number" if is_empty_string( ac )
    
      val existingProtIdents = protIdentsByAc(ac)
      val nbProtIdents = defined existingProtIdents ? scalar(existingProtIdents) : 0
      
      val tmpProtIdent
      if( nbProtIdents == 0 ) { push( unmappedAccessions, ac ) }
      else if( nbProtIdents == 1 ) { tmpProtIdent = existingProtIdents.(0) }
      else {
        val activeProtIdents = grep { _.isActive } existingProtIdents
        val nbActiveProtIdents = scalar(activeProtIdents)
        
        if( nbActiveProtIdents == 1 ) { tmpProtIdent = activeProtIdents(0) }
        else {
          
          ////// TODO: find a way to retrieve the protein identifier namespace
          warn "_update_rs_protein_matches: accession conflict = the accession number ac corresponds to more than one protein identifier"
          
          push( unmappedAccessions, ac )
        }
      }
      
      if( defined tmpProtIdent ) {
        proteinMatch._setTaxonId( tmpProtIdent.taxonId )
        
        ////// Link protein match to the stored protein sequence
        val rdbMsiProtein = rdbProtBySeq( tmpProtIdent.bioSequence.sequence )
        proteinMatch._setProteinId( rdbMsiProtein.id )
        
        ////// Update protein match description if ! defined
        if( is_empty_string( proteinMatch.description ) ) {
          
          val proteinName = protNameByTaxonAndId( tmpProtIdent.taxonId.'%'.tmpProtIdent.bioSequenceId )
          
          if( defined proteinName ) { proteinMatch.description( proteinName ) }
          else { warn "can't retrieve a protein name for accession 'ac' with taxon_id=" .tmpProtIdent.taxonId ." and bio_sequence_id=". tmpProtIdent.bioSequenceId }
          
        }
        
        ////// Update protein match coverage if ! defined
        if( ! defined proteinMatch.coverage ) {
          
          val seqPositions = map { ( _.start, _.end ) } @{proteinMatch.sequenceMatches}
          proteinMatch.coverage( proteinHelper.calcSequenceCoverage( rdbMsiProtein.length, seqPositions ) )
        }
        
      }
      
      ////// Set a null coverage to the protein match if still undefined
      if( ! defined proteinMatch.coverage ) { proteinMatch.coverage( 0 ) }
      
      */
    }
    
    unmappedAccessions.toArray
  }

  private def _updateRsSequenceMatches( resultSet: ResultSet, peptideMatchByTmpId: Map[Int,PeptideMatch] ): Unit = {
    
    // Retrieve some vars
    val rsId = resultSet.id
    val isDecoy = resultSet.isDecoy
    val proteinMatches = resultSet.proteinMatches
    
    // Iterate over protein matches
    for( proteinMatch <- proteinMatches ) {
      
      for( seqMatch <- proteinMatch.sequenceMatches ) {
        
        // Retrieve corresponding peptide match
        val peptideMatch = seqMatch.bestPeptideMatch.getOrElse( peptideMatchByTmpId( seqMatch.getBestPeptideMatchId ) )
        
        // Update peptide match id and result set id
        seqMatch.peptide = Some( peptideMatch.peptide )
        seqMatch.bestPeptideMatch = Some( peptideMatch )
        seqMatch.resultSetId = rsId
        
      }
    }
  }
  
  // TODO: implement this feature using the PSdb
  // Note: this method is a fake
  /*private def storeNewPeptidesInPsDb( peptides: Seq[Peptide] ): Array[Peptide] = {
    
    val insertedPeptides = new ArrayBuffer[Peptide](0)
    for( peptide <- peptides ) {
      if( peptide.id < 0 ) { insertedPeptides += peptide.copy( id = -peptide.id ) }
    }
    
    insertedPeptides.toArray
    
  }*/
  
}



// TODO: put these definitions in an other package (msi.table_definitions)
trait TableDefinition {
  
  val tableName: String
  val columns: Enumeration
  
  def getColumnsAsStrList(): List[String] = {
    List() ++ this.columns.values map { _.toString }
  }
  
  // TODO: implicit conversion
  def _getColumnsAsStrList[A <: Enumeration]( f: A => List[Enumeration#Value] ): List[String] = {
    List() ++ f(this.columns.asInstanceOf[A]) map { _.toString }
  }
  
  def getInsertQuery(): String = {
    this.buildInsertQuery( this.getColumnsAsStrList )
  }
  
  // TODO: implicit conversion
  def _getInsertQuery[A <: Enumeration]( f: A => List[Enumeration#Value] ): String = {
    this.buildInsertQuery( this._getColumnsAsStrList[A]( f ) )    
  }
  
  private def buildInsertQuery( colsAsStrList: List[String] ): String = {
    val valuesStr = List.fill(colsAsStrList.length)("?").mkString(",")

    "INSERT INTO "+ this.tableName+" ("+ colsAsStrList.mkString(",") +") VALUES ("+valuesStr+")"
  }
  
}

object ResultSetTable extends TableDefinition {
  
  val tableName = "result_set"
    
  object columns extends Enumeration {
    //type column = Value
    val id = Value("id")
    val name = Value("name")
    val description = Value("description")
    val `type` = Value("type")
    val modificationTimestamp = Value("modification_timestamp")
    val serializedProperties = Value("serialized_properties")
    val decoyResultSetId = Value("decoy_result_set_id")
    val msiSearchId = Value("msi_search_id")
  }
  
  def getColumnsAsStrList( f: ResultSetTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[ResultSetTable.columns.type]( f )
  }
  
  def getInsertQuery( f: ResultSetTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[ResultSetTable.columns.type]( f )
  }

}

object PeptideMatchTable extends TableDefinition {
  
  val tableName = "peptide_match"
    
  object columns extends Enumeration {
    //type column = Value
    val id = Value("id")
    val charge = Value("charge")
    val experimentalMoz = Value("experimental_moz")
    val elutionValue = Value("elution_value")
    val elutionUnit = Value("elution_unit")
    val score = Value("score")
    val rank = Value("rank")
    val deltaMoz = Value("delta_moz")
    val missedCleavage = Value("missed_cleavage")
    val fragmentMatchCount = Value("fragment_match_count")
    val isDecoy = Value("is_decoy")
    val serializedProperties = Value("serialized_properties")
    val peptideId = Value("peptide_id")
    val msQueryId = Value("ms_query_id")
    val bestChildId = Value("best_child_id")
    val scoringId = Value("scoring_id")
    val resultSetId = Value("result_set_id")
  }
  
  def getColumnsAsStrList( f: PeptideMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[PeptideMatchTable.columns.type]( f )
  }
  
  def getInsertQuery( f: PeptideMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[PeptideMatchTable.columns.type]( f )
  }
  
}

object ProteinMatchTable extends TableDefinition {
  
  val tableName = "protein_match"

  object columns extends Enumeration {
    //type column = Value
    val id = Value("id")
    val accession = Value("accession")
    val description = Value("description")
    val geneName = Value("gene_name")
    val score = Value("score")
    val coverage = Value("coverage")
    val peptideCount = Value("peptide_count")
    val peptideMatchCount = Value("peptide_match_count")
    val isDecoy = Value("is_decoy")
    val serializedProperties = Value("serialized_properties")
    val taxonId = Value("taxon_id")
    val bioSequenceId = Value("bio_sequence_id")
    val scoringId = Value("scoring_id")
    val resultSetId = Value("result_set_id")
  }
  
  def getColumnsAsStrList( f: ProteinMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[ProteinMatchTable.columns.type]( f )
  }
  
  def getInsertQuery( f: ProteinMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[ProteinMatchTable.columns.type]( f )
  }
  
}

object SequenceMatchTable extends TableDefinition {
  
  val tableName = "sequence_match"

  object columns extends Enumeration {
    //type column = Value
    val proteinMatchId = Value("protein_match_id")
    val peptideId = Value("peptide_id")
    val start = Value("start")
    val stop = Value("stop")
    val residueBefore = Value("residue_before")
    val residueAfter = Value("residue_after")
    val isDecoy = Value("is_decoy")
    val serializedProperties = Value("serialized_properties")
    val bestPeptideMatchId = Value("best_peptide_match_id")
    val bioSequenceId = Value("bio_sequence_id")
    val resultSetId = Value("result_set_id")
  }
  
  def getColumnsAsStrList( f: SequenceMatchTable.columns.type => List[Enumeration#Value] ): List[String] = {
    this._getColumnsAsStrList[SequenceMatchTable.columns.type]( f )
  }
  
  def getInsertQuery( f: SequenceMatchTable.columns.type => List[Enumeration#Value] ): String = {
    this._getInsertQuery[SequenceMatchTable.columns.type]( f )
  }
  
}

