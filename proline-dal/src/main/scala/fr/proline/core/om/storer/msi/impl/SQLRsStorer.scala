package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.profi.jdbc.easy._
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal._
import fr.proline.core.dal.tables.msi.MsiDbResultSetTable
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi._
import fr.proline.core.orm.msi.PeptideReadablePtmString
import fr.proline.core.orm.msi.ResultSet.{Type => RSType}

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap, LongMap}

class SQLRsStorer(
  val rsWriter: IRsWriter,
  val msiSearchWriter: Option[IMsiSearchWriter] = None,
  override val pklWriter: Option[IPeaklistWriter] = None
) extends AbstractRsStorer(pklWriter) with LazyLogging {

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
    // TODO: retrieve seqDbIdByTmpId in an other way (use context entity cache)
    if (resultSet.isSearchResult) this._storeResultSet(resultSet, context.seqDbIdByTmpId, context)
    else this._storeResultSet(resultSet, context)
  }

  // Only for search results
  private def _storeResultSet(resultSet: ResultSet, seqDbIdByTmpId: LongMap[Long], context: StorerContext): Long = {
    require(resultSet.isSearchResult, "a search result must be provided to this method")

    this._insertResultSet(resultSet, context)
    this._storeSearchResultObjects(resultSet, seqDbIdByTmpId, context)

    resultSet.id
  }

  // Only for user result sets
  private def _storeResultSet(resultSet: ResultSet, context: StorerContext): Long = {
    require(resultSet.isSearchResult == false, "this method can't deal with search result")

    this._insertResultSet(resultSet, context)
    this._storeUserResultSetObjects(resultSet, context)

    resultSet.id
  }

  private def _insertResultSet(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    DoJDBCWork.withEzDBC(context.getMSIDbConnectionContext) { msiEzDBC =>

      // Define some vars
      val isDecoy = resultSet.isDecoy
      val rsName = if (resultSet.name == null) None else Some(resultSet.name)
      val rsDesc = if (resultSet.description == null) None else Some(resultSet.description)

      val rsType = if (resultSet.isSearchResult) if (isDecoy) RSType.DECOY_SEARCH else RSType.SEARCH
      else if (isDecoy) RSType.DECOY_USER else RSType.USER

      val decoyRsIdOpt = if (resultSet.getDecoyResultSetId > 0) Some(resultSet.getDecoyResultSetId) else None
      val mergedRsmIdOpt = if (resultSet.mergedResultSummaryId > 0L) Some(resultSet.mergedResultSummaryId) else None
      val msiSearchIdOpt = resultSet.msiSearch.map(_.id)

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
          decoyRsIdOpt,
          mergedRsmIdOpt,
          msiSearchIdOpt
        )

        resultSet.id = stmt.generatedLong

        logger.debug("Created Result Set with ID=" + resultSet.id)
      }

    }

  }

  private def _storeSearchResultObjects(
    resultSet: ResultSet,
    seqDbIdByTmpId: LongMap[Long],
    context: StorerContext
  ): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    // Retrieve the list of existing peptides, filtered by searched peptides sequences, in the current MSIdb
    val existingMsiPepIdsByKey = this.rsWriter.fetchExistingPeptidesIdByUniqueKey(resultSet.getUniquePeptideSequences, msiDb)
    logger.info(s"${existingMsiPepIdsByKey.size} peptides have been loaded from the MSIdb")

    // Retrieve existing peptides and map them by unique key
    val totalPeptidesCount = resultSet.peptides.length
    val newPeptides = new ArrayBuffer[Peptide](resultSet.peptides.length)
    
    for (peptide <- resultSet.peptides) {
      val uniqueKey = peptide.uniqueKey
      val existingPepIdOpt = existingMsiPepIdsByKey.get(uniqueKey)
      
      // Update peptide id
      if( existingPepIdOpt.isDefined ) {
        peptide.id = existingPepIdOpt.get
      } else {
        newPeptides += peptide
      }
    }
    val newPeptidesCount = newPeptides.length
    val existingPeptidesCount = totalPeptidesCount - newPeptidesCount
    logger.debug(s"$existingPeptidesCount existing peptides in MSIdb related to result set '${resultSet.name}'" )

    if (newPeptidesCount > 0) {
      
      logger.info(s"Storing $newPeptidesCount new peptides in the MSIdb...")

      // Insert new peptides in the MSIdb (ids will be automatically updated by the writer)
      PeptideWriter(msiDb.getDriverType).insertPeptides(newPeptides, context)
    }

    // Store readable PTM strings
    val readablePtmStrCount = this.rsWriter.insertRsReadablePtmStrings(resultSet, msiDb)
    logger.info(readablePtmStrCount + " readable PTMs have been stored !")

    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches.mapByLong(_.id)

    // Update MS query id of peptide matches
    this._updateRsPeptideMatches(resultSet)

    // Store peptide matches
    val peptideMatchCount = this.rsWriter.insertRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " peptide matches have been stored !")

    // Retrieve protein matches and their accession numbers
    val proteinMatches = resultSet.proteinMatches
    val acNumbers = proteinMatches map { _.accession }

    // TODO: try to use the SeqRepo here
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

    logger.info( s"Storing ${proteinMatches.length} protein matches...")
 
    this._updateRsSeqDbProteinMatches(resultSet, null, null, null, seqDbIdByTmpId)

    // Store protein matches
    val proteinMatchesCount = this.rsWriter.insertRsProteinMatches(resultSet, msiDb)
    logger.info(proteinMatchesCount + " protein matches have been stored !")

    // Update sequence matches
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    val seqMatchesCount = this.rsWriter.insertRsSequenceMatches(resultSet, msiDb)
    logger.info(seqMatchesCount + " sequence matches have been stored !")

  }

  private def _storeUserResultSetObjects(resultSet: ResultSet, context: StorerContext): Unit = {

    val msiDb = context.getMSIDbConnectionContext

    //Get optional StorerContext  Map for Peptide ReadablePtmString.
    val readablePtmStringByPepId: Map[Long, PeptideReadablePtmString] = context.getEntityCache(classOf[PeptideReadablePtmString]).toMap
    // Store readable PTM strings
    val ptmCount = this.rsWriter.insertSpecifiedRsReadablePtmStrings(resultSet, readablePtmStringByPepId, msiDb)
    logger.info(ptmCount + " readable PTMs have been stored !")


    // Retrieve peptide matches
    val peptideMatches = resultSet.peptideMatches
    val peptideMatchByTmpId = peptideMatches.mapByLong(_.id)

    // Update RSId of peptide matches
    this._updateRsPeptideMatches(resultSet)

    // Store peptide matches
    val peptideMatchCount = this.rsWriter.insertRsPeptideMatches(resultSet, msiDb)
    logger.info(peptideMatchCount + " peptide matches have been stored !")

    // Update RSId of ProtienMatches
    this._updateRsProteinMatches(resultSet)
      
    // Store protein matches
    this.rsWriter.insertRsProteinMatches(resultSet, msiDb)
    logger.info("Result set protein matches have been stored !")

    // Update sequence matches (replace peptide match tmp ids by database ids)
    this._updateRsSequenceMatches(resultSet, peptideMatchByTmpId)

    // Store sequence matches
    this.rsWriter.insertRsSequenceMatches(resultSet, msiDb)
    logger.info("Result set sequence matches have been stored !")

  }

  private def _updateRsPeptideMatches(resultSet: ResultSet): Unit = {

    // Retrieve some vars
    val rsId = resultSet.id

    // Iterate over peptide matches to update their result set id
    resultSet.peptideMatches.foreach { _.resultSetId = rsId }

  }

  private def _updateRsProteinMatches(resultSet: ResultSet): Unit = {

    // Retrieve some vars
    val rsId = resultSet.id
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    for (proteinMatch <- proteinMatches) {
      proteinMatch.resultSetId = rsId
    }    
  }
  
  private def _updateRsSeqDbProteinMatches(
    resultSet: ResultSet,
    pdiProtBySeq: Map[String, Any],
    protIdentsByAc: Map[String, Any],
    protNameByTaxonAndId: Map[String, Any],
    seqDbIdByTmpId: LongMap[Long]
  ): Array[String] = {

    // Retrieve some vars
    val rsId = resultSet.id
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    val unmappedAccessions = new ArrayBuffer[String](0)
    for (proteinMatch <- proteinMatches) {
      if(proteinMatch.seqDatabaseIds !=null)
        proteinMatch.seqDatabaseIds = proteinMatch.seqDatabaseIds.map(seqDbIdByTmpId.get(_).getOrElse(0L)).filter { _ != 0 }
      proteinMatch.resultSetId = rsId

      // TODO: try to use the SeqRepo here
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

  private def _updateRsSequenceMatches(resultSet: ResultSet, peptideMatchByTmpId: LongMap[PeptideMatch]): Unit = {

    // Retrieve some vars
    val rsId = resultSet.id
    val isDecoy = resultSet.isDecoy
    val proteinMatches = resultSet.proteinMatches

    // Iterate over protein matches
    for (proteinMatch <- proteinMatches) {

      for (seqMatch <- proteinMatch.sequenceMatches) {

        // Retrieve corresponding peptide match
        val peptideMatchOpt = Option(seqMatch.bestPeptideMatch)
          .getOrElse(peptideMatchByTmpId.get(seqMatch.getBestPeptideMatchId))

        // Update peptide match id and result set id
        seqMatch.peptide = peptideMatchOpt.map(_.peptide)
        seqMatch.bestPeptideMatch = peptideMatchOpt
        seqMatch.resultSetId = rsId
      }
    }
  }

  def storeMsiSearch(msiSearch: MSISearch, context: StorerContext): Long = {
    require(msiSearchWriter.isDefined, "A MSI search writer must be provided")

    // Synchronize some related objects with the UDSdb
    var retVal: Long = -1
    val udsCtxt = context.getUDSDbConnectionContext

    try {
      DoJDBCWork.withEzDBC(udsCtxt) { udsEzDBC =>
        val enzymes = msiSearch.searchSettings.usedEnzymes
        for (enzyme <- enzymes) {
          udsEzDBC.selectAndProcess("SELECT id FROM enzyme WHERE name = ?", enzyme.name) { r =>
            enzyme.id = r.nextLong
          }

          if (enzyme.id <= 0) {
            //Not define. Store new enzyme.
            udsEzDBC.executePrepared("INSERT INTO enzyme(name, cleavage_regexp, is_independant, is_semi_specific) VALUES(?,?,?,?)", true) { stmt =>
              stmt.executeWith(
                enzyme.name,
                if (enzyme.cleavageRegexp.isDefined) enzyme.cleavageRegexp.get else null,
                enzyme.isIndependant,
                enzyme.isSemiSpecific
              )
              enzyme.id = stmt.generatedLong
            }

            for(ec <- enzyme.enzymeCleavages) {
              udsEzDBC.executePrepared("INSERT INTO enzyme_cleavage(site, residues,restrictive_residues, enzyme_id) VALUES(?,?,?,?)") { stmt =>
                stmt.executeWith(
                  ec.site,
                  ec.residues,
                  ec.restrictiveResidues,
                  enzyme.id
                )
              }
            }

          }
        }
      }
         this.msiSearchWriter.get.insertMsiSearch(msiSearch, context)

    } catch {
      case t: Throwable => {
        logger.error("Error while storing enzyme", t)
        retVal
      }

    }

  }

  def storeMsQueries(msiSearchID: Long, msQueries: Seq[MsQuery], context: StorerContext): StorerContext = {
    require(msiSearchWriter.isDefined, "A MSI search writer must be provided")

    this.msiSearchWriter.get.insertMsQueries(msiSearchID, msQueries, context)
  }

}
