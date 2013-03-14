package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.IRsWriter
import fr.proline.core.om.storer.ps.PeptideStorer
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal._
import fr.proline.core.om.storer.msi.IMsiSearchStorer

class SQLRsStorer(
  private val _rsWriter: IRsWriter,
  private val _msiSearchStorer: IMsiSearchStorer,
  private val _pklWriter: IPeaklistWriter
) extends IRsStorer with Logging {

  protected val msiSearchStorer = _msiSearchStorer //new SQLiteMsiSearchStorer()

  val peptideByUniqueKey = _rsWriter.peptideByUniqueKey
  val proteinBySequence = _rsWriter.proteinBySequence

  def storeResultSet(resultSet: ResultSet, dbManager: IDataStoreConnectorFactory, projectId: Int = 0): Int = {
    var createdRsId: Int = 0

    var storerContext: StorerContext = null // For SQL use ONLY

    try {
      storerContext = new StorerContext(ContextFactory.buildExecutionContext(dbManager, projectId, false))
      createdRsId = storeResultSet(resultSet, storerContext)
    } finally {

      if (storerContext != null) {
        storerContext.closeAll()
      }

    }

    createdRsId
  }

  def storeResultSet(resultSet: ResultSet, context: StorerContext): Int = {
    require(resultSet != null, "ResultSet is null")

    if (resultSet.isNative) this._storeResultSet(resultSet, context.seqDbIdByTmpId, context)
    else this._storeResultSet(resultSet, context)
  }

  def storeResultSet(resultSet: ResultSet, msQueries: Seq[MsQuery], peakListContainer: IPeaklistContainer, context: StorerContext): Int = {
    throw new Exception("NYI")
  }

  // Only for native result sets
  def _storeResultSet(resultSet: ResultSet, seqDbIdByTmpId: Map[Int, Int], context: StorerContext): Int = {
    require(resultSet.isNative, "too many arguments for a non native result set")

    this._insertResultSet(resultSet, context)
    this._storeNativeResultSetObjects(resultSet, seqDbIdByTmpId, context)

    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
    resultSet.id
  }

  // Only for non native result sets
  def _storeResultSet(resultSet: ResultSet, context: StorerContext): Int = {
    require(resultSet.isNative == false, "not enough arguments for a native result set")

    this._insertResultSet(resultSet, context)
    this._storeNonNativeResultSetObjects(resultSet, context)

    // Clear mappings which are now inconsistent because of PKs update
    //resultSet.clearMappings
    resultSet.id
  }

  private def _insertResultSet(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext
    
    DoJDBCWork.withEzDBC( context.getMSIDbConnectionContext, { msiEzDBC =>

      // Define some vars
      val isDecoy = resultSet.isDecoy
      val rsName = if (resultSet.name == null) None else Some(resultSet.name)
      val rsDesc = if (resultSet.description == null) None else Some(resultSet.description)
      var rsType = ""
      rsType = if (resultSet.isNative) "SEARCH" else "USER"
      rsType = if (isDecoy) "DECOY_" + rsType else rsType
  
      val decoyRsId = if (resultSet.getDecoyResultSetId > 0) Some(resultSet.getDecoyResultSetId) else None
      val msiSearchId = if (resultSet.msiSearch != null) Some(resultSet.msiSearch.id) else None
      // Store RDB result set
      // TODO: use JPA instead
  
      val rsInsertQuery = MsiDbResultSetTable.mkInsertQuery(t =>
        List(t.NAME, t.DESCRIPTION, t.TYPE, t.MODIFICATION_TIMESTAMP, t.DECOY_RESULT_SET_ID, t.MSI_SEARCH_ID))
  
      msiEzDBC.executePrepared(rsInsertQuery, true) { stmt =>
        stmt.executeWith(
          rsName,
          rsDesc,
          rsType,
          new java.util.Date,
          decoyRsId,
          msiSearchId
        )
  
        resultSet.id = stmt.generatedInt
        
        logger.debug("Created Result Set with ID " + resultSet.id)
      }
      
    },true)

  }

  private def _storeNativeResultSetObjects(resultSet: ResultSet, seqDbIdByTmpId: Map[Int, Int], context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    /*val rsPeptides = resultSet.peptides
    if( rsPeptides.find( _.id < 0 ) != None )
      throw new Exception("result set peptides must first be persisted")    
    
    val rsProteins = resultSet.getProteins.getOrElse( new Array[Protein](0) )
    if( rsProteins.find( _.id < 0 ) != None )
      throw new Exception("result set proteins must first be persisted")*/

    // Retrieve the list of existing peptides in the current MSIdb
    // TODO: do this using the PSdb
    val existingMsiPeptidesIdByKey = this._rsWriter.fetchExistingPeptidesIdByUniqueKey(resultSet.getUniquePeptideSequences, msiDb)
    logger.info(existingMsiPeptidesIdByKey.size + " existing peptides have been loaded from the database !")

    // Retrieve existing peptides and map them by unique key
    val (peptidesInMsiDb, newMsiPeptides) = resultSet.peptides.partition(pep => existingMsiPeptidesIdByKey.contains(pep.uniqueKey))
    for (peptide <- peptidesInMsiDb) {
      peptide.id = existingMsiPeptidesIdByKey(peptide.uniqueKey)
      this.peptideByUniqueKey += (peptide.uniqueKey -> peptide)
    }

    val nbNewMsiPeptides = newMsiPeptides.length
    if (nbNewMsiPeptides > 0) {

      import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
      import fr.proline.core.om.storer.ps.PeptideStorer

      val psDbCtx = context.getPSDbConnectionContext

      /* Create a specific SQL Context if needed */
      val psSQLDbCtx = if (psDbCtx.isInstanceOf[SQLConnectionContext]) {
        psDbCtx.asInstanceOf[SQLConnectionContext]
      } else {
        new SQLConnectionContext(psDbCtx)
      }

      // Define some vars
      val newMsiPepSeqs = newMsiPeptides.map { _.sequence }
      val newMsiPepKeySet = newMsiPeptides.map { _.uniqueKey } toSet
      val psPeptideProvider = new SQLPeptideProvider(psSQLDbCtx)
      
      // Retrieve peptide sequences already existing in the PsDb
      val peptidesForSeqsInPsDb = psPeptideProvider.getPeptidesForSequences(newMsiPepSeqs)
      // Build a map of existing peptides in PsDb
      val psPeptideByUniqueKey = peptidesForSeqsInPsDb.map { pep => (pep.uniqueKey -> pep) } toMap

      // Retrieve peptides which don't exist in the PsDb
      //val( peptidesInPsDb, newPsPeptides ) = newMsiPeptides.partition( pep => psPeptidesByUniqueKey.contains(pep.uniqueKey) )
      val newPsPeptides = newMsiPeptides filter { pep => !psPeptideByUniqueKey.contains(pep.uniqueKey) }
      // Retrieve peptides which exist in the PsDb but not in the MsiDb
      val peptidesInPsDb = peptidesForSeqsInPsDb filter { pep => newMsiPepKeySet.contains(pep.uniqueKey) }
  
      // Store missing PsDb peptides
      psDbCtx.beginTransaction()
      new PeptideStorer().storePeptides(newPsPeptides,psDbCtx)
      psDbCtx.commitTransaction()

      //psDb.closeConnection()

      // Map id of existing peptides and newly inserted peptides by their unique key
      val newMsiPepIdByUniqueKey = new collection.mutable.HashMap[String, Int]
      for (peptide <- peptidesInPsDb ++ newPsPeptides) {
        newMsiPepIdByUniqueKey += (peptide.uniqueKey -> peptide.id)
        //this.peptideByUniqueKey += ( peptide.uniqueKey -> peptide )
      }

      // Update id of new MsiDb peptides
      for (peptide <- newMsiPeptides) {
        peptide.id = newMsiPepIdByUniqueKey(peptide.uniqueKey)
        //peptide.id = this.peptideByUniqueKey( peptide.uniqueKey ).id
      }

      logger.info("storing " + nbNewMsiPeptides + " new peptides in the MSIdb...")
      _rsWriter.storeNewPeptides(newMsiPeptides, msiDb)
      logger.info(newMsiPeptides.length + " new peptides have been effectively stored !")

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
    val peptideMatchByTmpId = peptideMatches.map { pepMatch => pepMatch.id -> pepMatch } toMap

    // Update MS query id of peptide matches
    this._updateRsPeptideMatches(resultSet)

    // Store peptide matches
    val peptideMatchCount = this._rsWriter.storeRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " peptide matches have been stored !")

    // Retrieve protein matches and their accession numbers
    val proteinMatches = resultSet.proteinMatches
    val acNumbers = proteinMatches map { _.accession }

    logger.info(proteinMatches.length + " protein identifiers are going to be loaded...")

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

    this._updateRsProteinMatches(resultSet, null, null, null, seqDbIdByTmpId)

    // Store protein matches
    this._rsWriter.storeRsProteinMatches(resultSet, msiDb)
    logger.info("protein matches have been stored")

    // Update sequence matches
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    this._rsWriter.storeRsSequenceMatches(resultSet, msiDb)
    logger.info("sequence matches have been stored")

  }

  private def _storeNonNativeResultSetObjects(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    //resultSet.updateRsIdOfAllObjects() ////// is it still needed ?

    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches map { pepMatch => pepMatch.id -> pepMatch } toMap

    // Store peptide matches
    val peptideMatchCount = this._rsWriter.storeRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " peptide matches have been stored !")

    // Store protein matches
    this._rsWriter.storeRsProteinMatches(resultSet, msiDb)
    logger.info("protein matches have been stored")

    // Update sequence matches (replace peptide match tmp ids by database ids)
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    this._rsWriter.storeRsSequenceMatches(resultSet, msiDb)
    logger.info("sequence matches have been stored")

  }

  private def _updateRsPeptideMatches(resultSet: ResultSet): Unit = {

    // msQueryIdByTmpId: Map[Int,Int]

    // Retrieve some vars
    val rsId = resultSet.id

    // Iterate over peptide matches to update their result set id
    resultSet.peptideMatches.foreach { _.resultSetId = rsId }

  }

  private def _updateRsProteinMatches(resultSet: ResultSet,
    rdbProtBySeq: Map[String, Any],
    protIdentsByAc: Map[String, Any],
    protNameByTaxonAndId: Map[String, Any],
    seqDbIdByTmpId: Map[Int, Int]): Array[String] = {

    // Retrieve some vars
    val rsId = resultSet.id
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    val unmappedAccessions = new ArrayBuffer[String](0)
    for (proteinMatch <- proteinMatches) {

      proteinMatch.seqDatabaseIds = proteinMatch.seqDatabaseIds.map { seqDbIdByTmpId.get(_).getOrElse(0) }
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

  private def _updateRsSequenceMatches(resultSet: ResultSet, peptideMatchByTmpId: Map[Int, PeptideMatch]): Unit = {

    // Retrieve some vars
    val rsId = resultSet.id
    val isDecoy = resultSet.isDecoy
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    for (proteinMatch <- proteinMatches) {

      for (seqMatch <- proteinMatch.sequenceMatches) {

        // Retrieve corresponding peptide match
        val peptideMatch = Option(seqMatch.bestPeptideMatch).getOrElse(None).getOrElse(peptideMatchByTmpId(seqMatch.getBestPeptideMatchId))

        // Update peptide match id and result set id
        seqMatch.peptide = Some(peptideMatch.peptide)
        seqMatch.bestPeptideMatch = Some(peptideMatch)
        seqMatch.resultSetId = rsId

      }
    }
  }

  def storePeaklist(peaklist: Peaklist, context: StorerContext): Int = {
    logger.info("storing peaklist...")
    _pklWriter.storePeaklist(peaklist, context)
  }

  def storeSpectra(peaklistId: Int, peaklistContainer: IPeaklistContainer, context: StorerContext): StorerContext = {
    logger.info("storing spectra...")
    _pklWriter.storeSpectra(peaklistId, peaklistContainer, context)
  }

  def storeMsiSearch(msiSearch: MSISearch, context: StorerContext): Int = {

    import fr.proline.util.primitives.LongOrIntAsInt._

    // Synchronize the some related objects with the UDSdb
    val udsDbWork = BuildJDBCWork.withEzDBC(context.getUDSDbConnectionContext.getDriverType, { udsEzDBC =>
      val enzymes = msiSearch.searchSettings.usedEnzymes
      for (enzyme <- enzymes) {
        udsEzDBC.selectAndProcess("SELECT id FROM enzyme WHERE name = ?", enzyme.name) { r =>
          enzyme.id = r.nextAnyVal
        }
        require(enzyme.id > 0, "can't find an enzyme named '" + enzyme.name + "' in the UDS-DB")
      }
    })
    context.getUDSDbConnectionContext.doWork(udsDbWork, true)

    this.msiSearchStorer.storeMsiSearch(msiSearch, context)
  }

  def storeMsQueries(msiSearchID: Int, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {
    this.msiSearchStorer.storeMsQueries(msiSearchID, msQueries, context)
  }

  def insertInstrumentConfig(instrumentConfig: InstrumentConfig, context: StorerContext) = {
    this.msiSearchStorer.insertInstrumentConfig(instrumentConfig, context)
  }

}
