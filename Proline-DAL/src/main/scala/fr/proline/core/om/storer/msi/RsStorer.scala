package fr.proline.core.om.storer.msi

import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import net.noerd.prequel.ReusableStatement
import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.SQLFormatterImplicits._
import fr.proline.core.dal.{MsiDb,PsDb}
import fr.proline.core.dal.MsiDbResultSetTable
import fr.proline.core.utils.sql.BoolToSQLStr
import fr.proline.core.om.model.msi._
import fr.proline.core.dal.DatabaseManagement
import fr.proline.repository.DatabaseConnector

trait IRsStorer extends Logging {
  
  val msiDb1: MsiDb // Main MSI db connection
  lazy val msiDb2: MsiDb = new MsiDb( msiDb1.config, maxVariableNumber = 10000 ) // Secondary MSI db connection
  
  val scoringIdByType = new fr.proline.core.dal.helper.MsiDbHelper( msiDb1 ).getScoringIdByType
  
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

  def apply(dbMgmt: DatabaseManagement, msiDb: MsiDb): RsStorer = {
    msiDb.config.driver match {
    case "org.postgresql.Driver" => new RsStorer( dbMgmt, new PgRsStorer( msiDb ) )
    case "org.sqlite.JDBC" => new RsStorer( dbMgmt, new SQLiteRsStorer(msiDb ))
    case _ => new RsStorer( dbMgmt, new GenericRsStorer(msiDb ))
    }
  }
  
  def apply(dbMgmt: DatabaseManagement, projectID: Int): RsStorer = {
    val msiDbConnector = dbMgmt.getMSIDatabaseConnector(projectID,false)
    msiDbConnector.getProperty(DatabaseConnector.PROPERTY_DRIVERCLASSNAME) match {
    case "org.postgresql.Driver" => new RsStorer( dbMgmt, new PgRsStorer( new MsiDb(MsiDb.buildConfigFromDatabaseConnector(msiDbConnector))) )
    case "org.sqlite.JDBC" => new RsStorer( dbMgmt, new SQLiteRsStorer( new MsiDb(MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) ) ))
    case _ => new RsStorer( dbMgmt, new GenericRsStorer( new MsiDb(MsiDb.buildConfigFromDatabaseConnector(msiDbConnector) ) ))
    }
  }

}



class RsStorer( dbMgmt: DatabaseManagement, private val _storer: IRsStorer ) extends Logging {
  
  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  val msiDb1 = _storer.msiDb1
  lazy val psDb = new PsDb(PsDb.buildConfigFromDatabaseConnector(dbMgmt.psDBConnector) ) 
    
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
    val msiSearchId = if( resultSet.msiSearch != null ) Some(resultSet.msiSearch.id) else None
    // Store RDB result set
    // TODO: use JPA instead
    
    val rsInsertQuery = MsiDbResultSetTable.makeInsertQuery { t =>
      List( t.name, t.description, t.`type`, t.modificationTimestamp, t.decoyResultSetId, t.msiSearchId )
    }
    
    val stmt = msiDbConn.prepareStatement( rsInsertQuery, java.sql.Statement.RETURN_GENERATED_KEYS )     
    new ReusableStatement( stmt, msiDb1.config.sqlFormatter ) <<
      rsName <<
      rsDesc <<
      rsType <<
      new java.util.Date << // msiDb1.stringifyDate( new java.util.Date )
      decoyRsId <<
      msiSearchId

    stmt.execute()
    resultSet.id = this.msiDb1.extractGeneratedInt( stmt )
    logger.debug("Created Result Set with ID "+ resultSet.id)
  }
  
  private def _storeNativeResultSetObjects( resultSet: ResultSet, seqDbIdByTmpId: Map[Int,Int] ): Unit = {
    
    /*val rsPeptides = resultSet.peptides
    if( rsPeptides.find( _.id < 0 ) != None )
      throw new Exception("result set peptides must first be persisted")    
    
    val rsProteins = resultSet.getProteins.getOrElse( new Array[Protein](0) )
    if( rsProteins.find( _.id < 0 ) != None )
      throw new Exception("result set proteins must first be persisted")*/
    
    // Retrieve the list of existing peptides in the current MSIdb
    // TODO: do this using the PSdb
    val existingMsiPeptidesIdByKey = this._storer.fetchExistingPeptidesIdByUniqueKey( resultSet.getUniquePeptideSequences )
    logger.info( existingMsiPeptidesIdByKey.size + " existing peptides have been loaded from the database !" )
    
    // Retrieve existing peptides and map them by unique key
    val( peptidesInMsiDb, newMsiPeptides ) = resultSet.peptides.partition( pep => existingMsiPeptidesIdByKey.contains(pep.uniqueKey) )
    for( peptide <- peptidesInMsiDb ) {
      peptide.id = existingMsiPeptidesIdByKey( peptide.uniqueKey )
      this.peptideByUniqueKey += ( peptide.uniqueKey -> peptide )
    }
    
    val nbNewMsiPeptides = newMsiPeptides.length
    if( nbNewMsiPeptides > 0 ) {
      
      import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
      import fr.proline.core.om.storer.ps.PeptideStorer
      
      val psPeptideProvider = new SQLPeptideProvider( this.psDb )
      val psPeptideStorer = new PeptideStorer( this.psDb )
      
      // Define some vars
      val newMsiPepSeqs = newMsiPeptides.map { _.sequence }
      val newMsiPepKeySet = newMsiPeptides.map { _.uniqueKey } toSet
      
      // Retrieve peptide sequences already existing in the PsDb
      val peptidesForSeqsInPsDb = psPeptideProvider.getPeptidesForSequences( newMsiPepSeqs )      
      // Build a map of existing peptides in PsDb
      val psPeptideByUniqueKey = peptidesForSeqsInPsDb.map { pep => ( pep.uniqueKey -> pep ) } toMap
      
      // Retrieve peptides which don't exist in the PsDb
      //val( peptidesInPsDb, newPsPeptides ) = newMsiPeptides.partition( pep => psPeptidesByUniqueKey.contains(pep.uniqueKey) )
      val newPsPeptides = newMsiPeptides filter { pep => ! psPeptideByUniqueKey.contains( pep.uniqueKey ) } 
      // Retrieve peptides which exist in the PsDb but not in the MsiDb
      val peptidesInPsDb = peptidesForSeqsInPsDb filter { pep => newMsiPepKeySet.contains( pep.uniqueKey ) }           
      
      // Store missing PsDb peptides
      psPeptideStorer.storePeptides( newPsPeptides )
      this.psDb.commitTransaction()
      
      // Map id of existing peptides and newly inserted peptides by their unique key
      val newMsiPepIdByUniqueKey = new collection.mutable.HashMap[String,Int]      
      for( peptide <- peptidesInPsDb ++ newPsPeptides ) {
        newMsiPepIdByUniqueKey += ( peptide.uniqueKey -> peptide.id )
        //this.peptideByUniqueKey += ( peptide.uniqueKey -> peptide )
      }
      
      // Update id of new MsiDb peptides
      for( peptide <- newMsiPeptides ) {
        peptide.id = newMsiPepIdByUniqueKey( peptide.uniqueKey )
        //peptide.id = this.peptideByUniqueKey( peptide.uniqueKey ).id
      }
      
      logger.info( "storing "+ nbNewMsiPeptides +" new peptides in the MSIdb...")
      val insertedPeptides = this._storer.storeNewPeptides( newMsiPeptides )
      logger.info( insertedPeptides.length + " new peptides have been effectively stored !")
      
      /*for( insertedPeptide <- insertedPeptides ) {
        this.peptideByUniqueKey += ( insertedPeptide.uniqueKey -> insertedPeptide )
      }*/
      
    }
    
    // Update id of result set peptides
    /*for( peptide <- resultSet.peptides ) {
      if( peptide.id < 0 ) print(".")
      //peptide.id = this.peptideByUniqueKey( peptide.uniqueKey ).id
    }*/
    
    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches. map { pepMatch => pepMatch.id -> pepMatch } toMap
    
    // Update MS query id of peptide matches
    this._updateRsPeptideMatches( resultSet )
    
    // Store peptide matches
    val peptideMatchCount = this._storer.storeRsPeptideMatches( resultSet )
    logger.info( peptideMatchCount+" peptide matches have been stored !" )
    
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
    logger.info( peptideMatchCount + " peptide matches have been stored !" )
    
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
        val peptideMatch = Option(seqMatch.bestPeptideMatch).getOrElse( None ).getOrElse(peptideMatchByTmpId( seqMatch.getBestPeptideMatchId ) )
        
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

/*
import fr.proline.core.utils.sql.TableDefinition

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
  
}*/

