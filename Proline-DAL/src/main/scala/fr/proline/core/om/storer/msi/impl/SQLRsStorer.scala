package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.storer.msi.IMsiSearchWriter
import fr.proline.core.om.storer.msi.IPeaklistWriter
import fr.proline.core.om.storer.msi.IRsStorer
import fr.proline.core.om.storer.msi.IRsWriter
import fr.proline.core.om.storer.ps.PeptideStorer
import fr.proline.core.orm.msi.ResultSet.{ Type => RSType }
import fr.proline.repository.IDataStoreConnectorFactory
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal._

class SQLRsStorer(
  val rsWriter: IRsWriter,
  val msiSearchWriter: Option[IMsiSearchWriter] = None,
  override val pklWriter: Option[IPeaklistWriter] = None) extends AbstractRsStorer(pklWriter) with Logging {

  // TODO: implement as InMemoryProvider
  val peptideByUniqueKey = new HashMap[String, Peptide]
  val proteinBySequence = new HashMap[String, Protein]

  def createResultSet(resultSet: ResultSet, context: StorerContext): Long = {
    require(resultSet != null, "ResultSet is null")

    // Store decoy result set if it is defined and not already stored
    if (resultSet.decoyResultSet.isDefined && resultSet.decoyResultSet.get.id <= 0) {
      this._createResultSet(resultSet.decoyResultSet.get, context)
    }

    // Store target result set
    this._createResultSet(resultSet, context)
  }

  private def _createResultSet(resultSet: ResultSet, context: StorerContext): Long = {
    // TODO: retrieve seqDbIdByTmpId in an other way
    if (resultSet.isNative) this._storeResultSet(resultSet, context.seqDbIdByTmpId, context)
    else this._storeResultSet(resultSet, context)
  }

  // Only for native result sets
  private def _storeResultSet(resultSet: ResultSet, seqDbIdByTmpId: Map[Long, Long], context: StorerContext): Long = {
    require(resultSet.isNative, "too many arguments for a non native result set")

    this._insertResultSet(resultSet, context)
    this._storeNativeResultSetObjects(resultSet, seqDbIdByTmpId, context)

    resultSet.id
  }

  // Only for non native result sets
  private def _storeResultSet(resultSet: ResultSet, context: StorerContext): Long = {
    require(resultSet.isNative == false, "not enough arguments for a native result set")

    this._insertResultSet(resultSet, context)
    this._storeNonNativeResultSetObjects(resultSet, context)

    resultSet.id
  }

  private def _insertResultSet(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext, { msiEzDBC =>

      // Define some vars
      val isDecoy = resultSet.isDecoy
      val rsName = if (resultSet.name == null) None else Some(resultSet.name)
      val rsDesc = if (resultSet.description == null) None else Some(resultSet.description)

      val rsType = if (resultSet.isNative) if (isDecoy) RSType.DECOY_SEARCH else RSType.SEARCH
      else if (isDecoy) RSType.DECOY_USER else RSType.USER
      //rsType = if (resultSet.isNative) "SEARCH" else "USER"
      //rsType = if (isDecoy) "DECOY_" + rsType else rsType

      val decoyRsId = if (resultSet.getDecoyResultSetId > 0) Some(resultSet.getDecoyResultSetId) else None
      val msiSearchId = resultSet.msiSearch.map(_.id)

      // Store RDB result set  
      val rsInsertQuery = MsiDbResultSetTable.mkInsertQuery((t, c) => c.filter(_ != t.ID))

      msiEzDBC.executePrepared(rsInsertQuery, true) { stmt =>
        stmt.executeWith(
          rsName,
          rsDesc,
          rsType.toString,
          Option.empty[String],
          new java.util.Date,
          resultSet.properties.map(ProfiJson.serialize(_)),
          decoyRsId,
          msiSearchId
        )

        resultSet.id = stmt.generatedLong

        logger.debug("Created Result Set with ID " + resultSet.id)
      }

    }, true)

  }

  private def _storeNativeResultSetObjects(resultSet: ResultSet, seqDbIdByTmpId: Map[Long, Long], context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    //    val rsPeptides = resultSet.peptides.filter(_.id < 0)
    //    val uniquePeptideSequences = rsPeptides.map(pep => {  	pep.sequence   }) distinct

    // Retrieve the list of existing peptides, filtered by searched peptides sequences, in the current MSIdb
    // TODO: do this using the PSdb

    val existingMsiPeptidesIdByKey = this.rsWriter.fetchExistingPeptidesIdByUniqueKey(resultSet.getUniquePeptideSequences, msiDb)
    logger.info(existingMsiPeptidesIdByKey.size + " existing peptides have been loaded from the MSIdb")

    // Retrieve existing peptides and map them by unique key
    val (peptidesInMsiDb, newMsiPeptides) = resultSet.peptides.partition(pep => existingMsiPeptidesIdByKey.contains(pep.uniqueKey))
    var nbrRealMSIPep = 0
    for (peptide <- peptidesInMsiDb) {
      peptide.id = existingMsiPeptidesIdByKey(peptide.uniqueKey)
      this.peptideByUniqueKey += (peptide.uniqueKey -> peptide)
      nbrRealMSIPep += 1
    }
    logger.debug(nbrRealMSIPep + " existing peptides in MSIdb related to result file peptide")

    val nbNewMsiPeptides = newMsiPeptides.length
    if (nbNewMsiPeptides > 0) {

      import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
      import fr.proline.core.om.storer.ps.PeptideStorer

      val psDbCtx = context.getPSDbConnectionContext

      // Define some vars
      val newMsiPepSeqs = newMsiPeptides.map { _.sequence }
      val newMsiPepKeySet = newMsiPeptides.map { _.uniqueKey } toSet
      val psPeptideProvider = new SQLPeptideProvider(psDbCtx)

      // Retrieve peptide sequences already existing in the PsDb
      val peptidesForSeqsInPsDb = psPeptideProvider.getPeptidesForSequences(newMsiPepSeqs)
      // Build a map of existing peptides in PsDb
      val psPeptideByUniqueKey = peptidesForSeqsInPsDb.map { pep => (pep.uniqueKey -> pep) } toMap

      // Retrieve peptides which don't exist in the PsDb
      //val( peptidesInPsDb, newPsPeptides ) = newMsiPeptides.partition( pep => psPeptidesByUniqueKey.contains(pep.uniqueKey) )
      val newPsPeptides = newMsiPeptides filter { pep => !psPeptideByUniqueKey.contains(pep.uniqueKey) }
      // Retrieve peptides which exist in the PsDb but not in the MsiDb
      val peptidesInPsDb = peptidesForSeqsInPsDb filter { pep => newMsiPepKeySet.contains(pep.uniqueKey) }

      logger.debug(peptidesInPsDb.length + " existing peptides have been loaded from the PSdb")

      // Store missing PsDb peptides
      psDbCtx.beginTransaction()
      new PeptideStorer().storePeptides(newPsPeptides, psDbCtx)
      psDbCtx.commitTransaction()

      //psDb.closeConnection()

      // Map id of existing peptides and newly inserted peptides by their unique key
      val newMsiPepIdByUniqueKey = new collection.mutable.HashMap[String, Long]
      for (peptide <- peptidesInPsDb ++ newPsPeptides) {
        newMsiPepIdByUniqueKey += (peptide.uniqueKey -> peptide.id)
        //this.peptideByUniqueKey += ( peptide.uniqueKey -> peptide )
      }

      // Update id of new MsiDb peptides
      for (peptide <- newMsiPeptides) {
        peptide.id = newMsiPepIdByUniqueKey(peptide.uniqueKey)
        //peptide.id = this.peptideByUniqueKey( peptide.uniqueKey ).id
      }

      logger.info("Storing " + nbNewMsiPeptides + " new Peptides in the MSIdb...")
      this.rsWriter.insertNewPeptides(newMsiPeptides, this.peptideByUniqueKey, msiDb)
      logger.info(newMsiPeptides.length + " new Peptides have been effectively stored")

      /*for( insertedPeptide <- insertedPeptides ) {
        this.peptideByUniqueKey += ( insertedPeptide.uniqueKey -> insertedPeptide )
      }*/

    }

    // Update id of result set peptides
    /*for( peptide <- resultSet.peptides ) {
      if( peptide.id < 0 ) print(".")
      //peptide.id = this.peptideByUniqueKey( peptide.uniqueKey ).id
    }*/

    // Store readable PTM strings
    val ptmCount = this.rsWriter.insertRsReadablePtmStrings(resultSet, msiDb)
    logger.info(ptmCount + " readable PTMs have been stored !")

    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches.map { pepMatch => pepMatch.id -> pepMatch } toMap

    // Update MS query id of peptide matches
    this._updateRsPeptideMatches(resultSet)

    // Store peptide matches
    val peptideMatchCount = this.rsWriter.insertRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " PeptideMatches have been stored !")

    // Retrieve protein matches and their accession numbers
    val proteinMatches = resultSet.proteinMatches
    val acNumbers = proteinMatches map { _.accession }

    logger.info(proteinMatches.length + " ProteinMatches are going to be stored...")

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
    val proteinMatchesCount = this.rsWriter.insertRsProteinMatches(resultSet, msiDb)
    logger.info(proteinMatchesCount + " ProteinMatches have been stored")

    // Update sequence matches
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    val seqMatchesCount = this.rsWriter.insertRsSequenceMatches(resultSet, msiDb)
    logger.info(seqMatchesCount + " SequenceMatches have been stored")

  }

  private def _storeNonNativeResultSetObjects(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    //resultSet.updateRsIdOfAllObjects() ////// is it still needed ?

    // Store readable PTM strings
    val ptmCount = this.rsWriter.insertRsReadablePtmStrings(resultSet, msiDb)
    logger.info(ptmCount + " readable PTMs have been stored !")

    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches map { pepMatch => pepMatch.id -> pepMatch } toMap

    // Store peptide matches
    val peptideMatchCount = this.rsWriter.insertRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " peptide matches have been stored !")

    // Store protein matches
    this.rsWriter.insertRsProteinMatches(resultSet, msiDb)
    logger.info("protein matches have been stored")

    // Update sequence matches (replace peptide match tmp ids by database ids)
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    this.rsWriter.insertRsSequenceMatches(resultSet, msiDb)
    logger.info("sequence matches have been stored")

  }

  private def _updateRsPeptideMatches(resultSet: ResultSet): Unit = {

    // Retrieve some vars
    val rsId = resultSet.id

    // Iterate over peptide matches to update their result set id
    resultSet.peptideMatches.foreach { _.resultSetId = rsId }

  }

  private def _updateRsProteinMatches(resultSet: ResultSet,
                                      rdbProtBySeq: Map[String, Any],
                                      protIdentsByAc: Map[String, Any],
                                      protNameByTaxonAndId: Map[String, Any],
                                      seqDbIdByTmpId: Map[Long, Long]): Array[String] = {

    // Retrieve some vars
    val rsId = resultSet.id
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    val unmappedAccessions = new ArrayBuffer[String](0)
    for (proteinMatch <- proteinMatches) {

      proteinMatch.seqDatabaseIds = proteinMatch.seqDatabaseIds.map(seqDbIdByTmpId.get(_).getOrElse(0L)).filter { _ != 0 }
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

  private def _updateRsSequenceMatches(resultSet: ResultSet, peptideMatchByTmpId: Map[Long, PeptideMatch]): Unit = {

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

  def storeMsiSearch(msiSearch: MSISearch, context: StorerContext): Long = {
    require(msiSearchWriter.isDefined, "A MSI search writer must be provided")

    import fr.proline.util.primitives._

    // Synchronize some related objects with the UDSdb
    DoJDBCWork.withEzDBC(context.getUDSDbConnectionContext, { udsEzDBC =>
      val enzymes = msiSearch.searchSettings.usedEnzymes
      for (enzyme <- enzymes) {
        udsEzDBC.selectAndProcess("SELECT id FROM enzyme WHERE name = ?", enzyme.name) { r =>
          enzyme.id = toLong(r.nextAny)
        }
        require(enzyme.id > 0, "can't find an enzyme named '" + enzyme.name + "' in the UDS-DB")
      }
    }, true)

    this.msiSearchWriter.get.insertMsiSearch(msiSearch, context)
  }

  def storeMsQueries(msiSearchID: Long, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {
    require(msiSearchWriter.isDefined, "A MSI search writer must be provided")

    this.msiSearchWriter.get.insertMsQueries(msiSearchID, msQueries, context)
  }

}
