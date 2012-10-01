package fr.proline.core.om.storer.msi.impl

import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.IRsWriter
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.dal.DatabaseManagement
import fr.proline.core.dal.MsiDbResultSetTable
import fr.proline.core.dal.PsDb
import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.HashMap
import fr.proline.core.utils.lzma.EasyLzma
import fr.proline.core.dal.MsiDbSearchSettingsTable
import fr.proline.core.dal.MsiDbMsiSearchTable
import fr.proline.core.dal.MsiDbUsedPtmTable
import fr.proline.core.dal.MsiDbPtmSpecificityTable
import fr.proline.core.dal.MsiDbSeqDatabaseTable
import fr.proline.core.dal.MsiDbMsQueryTable
import fr.proline.core.utils.sql._
import fr.proline.core.om.storer.msi.IPeaklistWriter
class SQLRsStorer ( dbMgmt: DatabaseManagement, private val _storer: IRsWriter, private val _plWriter: IPeaklistWriter) extends IRsStorer with Logging {

  import net.noerd.prequel.ReusableStatement
  import net.noerd.prequel.SQLFormatterImplicits._
  import fr.proline.core.dal.SQLFormatterImplicits._
  import fr.proline.core.utils.sql.BoolToSQLStr
  import fr.proline.core.om.model.msi._
  
  val msiDb1 = _storer.msiDb1
  lazy val psDb = new PsDb(PsDb.buildConfigFromDatabaseConnector(dbMgmt.psDBConnector) )
    
  val peptideByUniqueKey = _storer.peptideByUniqueKey
  val proteinBySequence = _storer.proteinBySequence
  
  def storeResultSet(resultSet : ResultSet, context : StorerContext) : Int  = {
    if(resultSet == null )
    	throw new IllegalArgumentException("ResultSet is null")
    
    var rId = 0
    
    if(resultSet.isNative)
    	rId =  _storeResultSet(resultSet, context.seqDbIdByTmpId)   
    else 
       rId =  _storeResultSet(resultSet )
     rId
  }
  
   
  def storeResultSet( resultSet: ResultSet ) : Int  = {
    val context = new StorerContext(dbMgmt, _storer.msiDb1.dbConnector)
    val createdRsId = storeResultSet(resultSet, context)
    context.closeOpenedEM()
    createdRsId
  }
  
  def storeResultSet(resultSet : ResultSet, msQueries : Seq [MsQuery], peakListContainer : IPeaklistContainer, context : StorerContext) : Int = {
    throw new Exception("NYI")
  }
   
    // Only for native result sets
  def _storeResultSet( resultSet: ResultSet, seqDbIdByTmpId: Map[Int,Int] ): Int = {
    
    require( resultSet.isNative, "too many arguments for a non native result set" )
    
    this._insertResultSet( resultSet )
    this._storeNativeResultSetObjects( resultSet, seqDbIdByTmpId )
    
    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
     resultSet.id
  }
  
  // Only for non native result sets
  def _storeResultSet( resultSet: ResultSet ): Int = {
    
    require( resultSet.isNative == false, "not enough arguments for a native result set" )
    
    this._insertResultSet( resultSet )
    this._storeNonNativeResultSetObjects( resultSet )
    
    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
    resultSet.id
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

    
  def storePeaklist( peaklist: Peaklist, context : StorerContext): Int = {   
    logger.info( "storing peaklist..." )
    _plWriter.storePeaklist(peaklist, context)

  }
  
  def storeSpectra( peaklistId: Int, peaklistContainer: IPeaklistContainer, context : StorerContext ): StorerContext = {
      logger.info( "storing spectra..." )
      _plWriter.storeSpectra(peaklistId, peaklistContainer, context)
   }
  
  def storeMsiSearch(msiSearch : MSISearch, context : StorerContext) : Int = {
    val ss = msiSearch.searchSettings
    
    // Insert sequence databases 
    // TODO : If seqDb does not exist in PDI do not create it !! 
    val _seqDbIdByTmpIdBuilder = collection.immutable.Map.newBuilder[Int,Int]
    ss.seqDatabases.foreach { seqDb =>
      val tmpSeqDbId = seqDb.id
      this._insertSeqDatabase( seqDb )
      _seqDbIdByTmpIdBuilder += ( tmpSeqDbId -> seqDb.id )
    }
    
    // Insert search settings    
    this._insertSearchSettings( ss )
    
    // Insert used PTMs
    val ssId = ss.id
    for( ptmDef <- ss.fixedPtmDefs ) this._insertUsedPTM( ssId, ptmDef, true )
    for( ptmDef <- ss.variablePtmDefs ) this._insertUsedPTM( ssId, ptmDef, false )
    
    // Insert MSI search
    this._insertMsiSearch( msiSearch )
    
    context.seqDbIdByTmpId =  _seqDbIdByTmpIdBuilder.result()
    msiSearch.id
  }
  
  def storeMsQueries( msiSearchID : Int,
                      msQueries: Seq[MsQuery],
                      context : StorerContext ): StorerContext = {
    
        
    val msQueryColsList = MsiDbMsQueryTable.getColumnsAsStrList().filter { _ != "id" }
    val msQueryInsertQuery = MsiDbMsQueryTable.makeInsertQuery( msQueryColsList )
    
    val msiDbTx = context.msiDB.getOrCreateTransaction()
    msiDbTx.executeBatch( msQueryInsertQuery, true ) { stmt =>
      
      for( msQuery <- msQueries ) {
        
        //val tmpMsQueryId = msQuery.id
        
        msQuery.msLevel match {
          case 1 => this._insertMsQuery( stmt, msQuery.asInstanceOf[Ms1Query], msiSearchID, Option.empty[Int], context )
          case 2 => {
            val ms2Query = msQuery.asInstanceOf[Ms2Query]
            // FIXME: it should not be null
            var spectrumId = Option.empty[Int]
            if( context.spectrumIdByTitle != null ) {
              ms2Query.spectrumId = context.spectrumIdByTitle(ms2Query.spectrumTitle)
              spectrumId = Some(ms2Query.spectrumId)
            }
            this._insertMsQuery( stmt, msQuery, msiSearchID, spectrumId, context )
          }
        }
        
        //msQueryIdByTmpId += ( tmpMsQueryId -> msQuery.id )
        
      }
      
    }
    context
  }
  
  
  // Duplicate AbstractRsStorer !
   def insertInstrumentConfig( instrumentConfig: InstrumentConfig, context : StorerContext ): Unit = {
    
    require( instrumentConfig.id > 0, "instrument configuration must have a strictly positive identifier" )
    
    // Check if the instrument config exists in the MSIdb
    val count = context.msiDB.getOrCreateTransaction.selectInt( "SELECT count(*) FROM instrument_config WHERE id=" + instrumentConfig.id )
    
    // If the instrument config doesn't exist in the MSIdb
    if( count == 0 ) {
      context.msiDB.getOrCreateTransaction.executeBatch("INSERT INTO instrument_config VALUES (?,?,?,?,?)") { stmt =>
        stmt.executeWith( instrumentConfig.id,
                          instrumentConfig.name,
                          instrumentConfig.ms1Analyzer,
                          Option(instrumentConfig.msnAnalyzer),
                          Option.empty[String]
                         )
      }
    }   
  }
    private def _insertMsQuery( stmt: ReusableStatement, msQuery: MsQuery, msiSearchId: Int, spectrumId: Option[Int], context : StorerContext ): Unit = {
    
    import com.codahale.jerkson.Json.generate
    // Retrieve some vars
    //val spectrumId = ms2Query.spectrumId
    //if( spectrumId <= 0 )
      //throw new Exception("spectrum must first be persisted")
    
    val msqPropsAsJSON = if( msQuery.properties != None ) Some(generate(msQuery.properties.get)) else None
    
    stmt.executeWith(
          msQuery.initialId,
          msQuery.charge,
          msQuery.moz,
          msqPropsAsJSON,
          spectrumId,
          msiSearchId
          )

    msQuery.id = context.msiDB.extractGeneratedInt( stmt.wrapped )
  }
    
  
  private def _insertSeqDatabase( seqDatabase: SeqDatabase ): Unit = {
    
    val fasta_path = seqDatabase.filePath
    val seqDbIds = msiDb1.getOrCreateTransaction.select( "SELECT id FROM seq_database WHERE fasta_file_path='" + fasta_path+"'" ) { _.nextInt.get }
    
    // If the sequence database doesn't exist in the MSIdb
    if( seqDbIds.length == 0 ) {
      
      val seqDbColsList = MsiDbSeqDatabaseTable.getColumnsAsStrList().filter { _ != "id" }
      val seqDbInsertQuery = MsiDbSeqDatabaseTable.makeInsertQuery( seqDbColsList )
      
      val msiDbTx = msiDb1.getOrCreateTransaction()
      msiDbTx.executeBatch( seqDbInsertQuery, true ) { stmt =>
      
        stmt.executeWith(
              seqDatabase.name,
              seqDatabase.filePath,
              seqDatabase.version,
              new java.util.Date,// TODO: upgrade to date seqDatabase.releaseDate,
              seqDatabase.sequencesCount,
              Option.empty[String]
            )
          
        seqDatabase.id = msiDb1.extractGeneratedInt( stmt.wrapped )
      }
      
    } else {
      seqDatabase.id = seqDbIds(0)
    }
    
  }
  
  private def _insertSearchSettings( searchSettings: SearchSettings ): Unit = {
    
    // Retrieve some vars
    val instrumentConfigId = searchSettings.instrumentConfig.id
    require( instrumentConfigId > 0, "instrument configuration must first be persisted" )
    
    val searchSettingsColsList = MsiDbSearchSettingsTable.getColumnsAsStrList().filter { _ != "id" }
    val searchSettingsInsertQuery = MsiDbSearchSettingsTable.makeInsertQuery( searchSettingsColsList )
    
    val msiDbTx = msiDb1.getOrCreateTransaction()
    msiDbTx.executeBatch( searchSettingsInsertQuery, true ) { stmt =>
      stmt.executeWith(
            searchSettings.softwareName,
            searchSettings.softwareVersion,
            searchSettings.taxonomy,
            searchSettings.maxMissedCleavages,
            searchSettings.ms1ChargeStates,
            searchSettings.ms1ErrorTol,
            searchSettings.ms1ErrorTolUnit,
            searchSettings.quantitation,
            searchSettings.isDecoy,
            Option.empty[String],
            searchSettings.instrumentConfig.id
            )
            
      searchSettings.id = msiDb1.extractGeneratedInt( stmt.wrapped )
    }
    
    // Link search settings to sequence databases
    msiDb1.getOrCreateTransaction.executeBatch( "INSERT INTO search_settings_seq_database_map VALUES (?,?,?,?)" ) { stmt =>
      searchSettings.seqDatabases.foreach { seqDb =>
        assert( seqDb.id > 0, "sequence database must first be persisted" )
        
        stmt.executeWith( searchSettings.id, seqDb.id, seqDb.sequencesCount, Option.empty[String] )
      }
    }
    
  }

 private def _insertUsedPTM( ssId: Int, ptmDef: PtmDefinition, isFixed: Boolean ): Unit = {
    
    // Check if the PTM specificity exists in the MSIdb
    val msiDbTx = msiDb1.getOrCreateTransaction()
    val count = msiDbTx.selectInt( "SELECT count(*) FROM ptm_specificity WHERE id =" + ptmDef.id )
    
    // Insert PTM specificity if it doesn't exist in the MSIdb
    if( count == 0 ) {
      val ptmSpecifColsList = MsiDbPtmSpecificityTable.getColumnsAsStrList()
      val ptmSpecifInsertQuery = MsiDbPtmSpecificityTable.makeInsertQuery( ptmSpecifColsList )      
      
      msiDbTx.executeBatch( ptmSpecifInsertQuery, false ) { stmt => {
        val residueAsStr = if(ptmDef.residue == '\0') "" else ptmDef.residue.toString
        logger.debug(" Execute "+ptmSpecifInsertQuery+" with "+ ptmDef.id+" - "+ptmDef.location+" - "+residueAsStr)
        stmt.executeWith(
              ptmDef.id,
              ptmDef.location,
              residueAsStr,
              Option.empty[String]
              )
        }
      }
    }
    
    // Link used PTMs to search settings
    val usedPtmColsList = MsiDbUsedPtmTable.getColumnsAsStrList()
    val usedPtmInsertQuery = MsiDbUsedPtmTable.makeInsertQuery( usedPtmColsList )
    msiDbTx.executeBatch( usedPtmInsertQuery, false ) { stmt =>
      stmt.executeWith(
            ssId,
            ptmDef.id,
            ptmDef.names.shortName,
            isFixed,
            Option.empty[String]
            )
            
    }
    
  }
 private def _insertMsiSearch( msiSearch: MSISearch ): Unit = {
    
    // Retrieve some vars
    val searchSettingsId = msiSearch.searchSettings.id
    require( searchSettingsId > 0, "search settings must first be persisted" )
    
    val peaklistId = msiSearch.peakList.id
    require( peaklistId > 0, "peaklist must first be persisted" )
    
    val msiSearchColsList = MsiDbMsiSearchTable.getColumnsAsStrList().filter { _ != "id" }
    val msiSearchInsertQuery = MsiDbMsiSearchTable.makeInsertQuery( msiSearchColsList )
    
    val msiDbTx = msiDb1.getOrCreateTransaction()
    msiDbTx.executeBatch( msiSearchInsertQuery, true ) { stmt =>
      stmt.executeWith(
            msiSearch.title,
            msiSearch.date, // msiDb.stringifyDate( msiSearch.date ),
            msiSearch.resultFileName,
            msiSearch.resultFileDirectory,
            msiSearch.jobNumber,
            msiSearch.userName,
            msiSearch.userEmail,
            msiSearch.queriesCount,
            msiSearch.submittedQueriesCount,
            msiSearch.searchedSequencesCount,
            Option.empty[String],
            searchSettingsId,
            peaklistId
          )
          
      msiSearch.id = msiDb1.extractGeneratedInt( stmt.wrapped )
    }
    
  }
}