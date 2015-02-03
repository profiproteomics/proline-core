package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.msi.IResultSetSplitter
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSetProperties
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.impl.SQLMsiSearchWriter
import fr.profi.util.StringUtils
import fr.proline.core.om.model.msi.PeptideMatch

/**
 * @author David Bouyssie
 *
 */
object ResultFileStorer extends Logging {

  /**
   * Store the IResultFile in current MSI db.
   * Transaction are not managed by this method, should be done by user.
   */
  def storeResultFile(
    storerContext: StorerContext,
    rsStorer: IRsStorer,
    resultFile: IResultFile,
    sqlCompatMode: Boolean, // TODO: remove me when SQL & JPA importers are removed
    targetDecoyMode: Option[String],
    acDecoyRegex: Option[util.matching.Regex] = None,
    saveSpectrumMatch: Boolean = false,
    rsSplitter: Option[IResultSetSplitter] = None
  ): ResultSet = {

    val start = System.currentTimeMillis()
    logger.info("Storing ResultFile " + resultFile.fileLocation.getName())

    // Store the instrument configuration
    this._insertInstrumentConfig(resultFile.instrumentConfig.get, storerContext)
    
    // Load target result set from result file
    val targetRs = resultFile.getResultSet(false)

    checkResultSet(targetRs, false)

    // Retrieve MSISearch and related MS queries
    val msiSearch = resultFile.msiSearch
    val msQueries = if (resultFile.msQueries == null) null else resultFile.msQueries.sortBy(_.initialId)

    if (StringUtils.isEmpty(targetRs.name)) targetRs.name = msiSearch.title

    // Update result set properties
    val rsProps = targetRs.properties.getOrElse(new ResultSetProperties)
    rsProps.setTargetDecoyMode(targetDecoyMode)
    targetRs.properties = Some(rsProps)

    // Update the peaklist software if needed
    if (resultFile.peaklistSoftware.isDefined && targetRs.msiSearch.isDefined) {
      targetRs.msiSearch.get.peakList.peaklistSoftware = resultFile.peaklistSoftware.get
    }

    // Restrieve or create a peaklist writer
    val pklWriter = rsStorer.getOrBuildPeaklistWriter(storerContext)

    // Insert the peaklist information
    msiSearch.peakList.id = pklWriter.insertPeaklist(msiSearch.peakList, storerContext)

    // Insert spectra contained in result file
    logger.info("Storing spectra...")
    pklWriter.insertSpectra(msiSearch.peakList.id, resultFile, storerContext)

    // Load and store decoy result set if it exists
    if (resultFile.hasDecoyResultSet) {

      logger.info("ResultFile has decoy ResultSet")

      // Load target result set from result file
      val decoyRs = resultFile.getResultSet(true)

      checkResultSet(targetRs, true)

      // Link decoy RS to target RS
      targetRs.decoyResultSet = Some(decoyRs)

      // compute pretty ranks for both result sets
      _setPrettyRanks(targetRs, Some(decoyRs))

      // Store target and decoy result sets
      rsStorer.storeResultSet(targetRs, msQueries, storerContext)

      if (saveSpectrumMatch) {
        // Insert target spectrum matches
        logger.info("Storing TARGET spectrum matches...")
        rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)

        // Insert decoy spectrum matches
        logger.info("Storing DECOY spectrum matches...")
        rsStorer.insertSpectrumMatches(decoyRs, resultFile, storerContext)
      }

      logger.info("ResultFile " + resultFile.fileLocation.getName() + " stored in " + (System.currentTimeMillis() - start) / 1000.0 + " s")
      return targetRs

    } // Else if a regex has been passed to detect decoy protein matches        
    else if (acDecoyRegex.isDefined) {

      logger.info("ResultFile contains decoy data, using regex to extract it")

      // Then split the result set into a target and a decoy one
      val (tRs, dRs) = rsSplitter.get.split(targetRs, acDecoyRegex.get)

      checkResultSet(tRs, false)
      checkResultSet(dRs, true)

      // Link decoy RS to target RS
      tRs.decoyResultSet = Some(dRs)

      // compute pretty ranks for both result sets
      _setPrettyRanks(tRs, Some(dRs))

      // Store target and decoy result sets
      rsStorer.storeResultSet(tRs, msQueries, storerContext)

      logger.debug {
        val targetRSId = if (tRs == null) { 0 } else { tRs.id }
        val decoyRSId = if (dRs == null) { 0 } else { dRs.id }

        "TARGET ResultSet {" + targetRSId + "}  DECOY ResultSet {" + decoyRSId + "}"
      }

      // Map peptide matches obtained after split by MS query id and peptide unique key
      val pepMatchMapAfterSplit = Map() ++ (tRs.peptideMatches ++ dRs.peptideMatches).map { pm =>
        (pm.msQuery.id, pm.peptide.uniqueKey) -> pm
      }

      // Update peptide match ids of result set provided before split (because peptide matches were cloned during split)
      targetRs.peptideMatches.foreach { pm =>
        pm.id = pepMatchMapAfterSplit((pm.msQuery.id, pm.peptide.uniqueKey)).id
      }

      if (saveSpectrumMatch) {
        // Insert target and decoy spectrum matches
        logger.info("Storing target and decoy spectrum matches...")
        rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)
      }

      logger.info("ResultFile " + resultFile.fileLocation.getName() + " stored in " + (System.currentTimeMillis() - start) / 1000.0 + " s")

      return tRs
    } else {

      logger.info("ResultFile is target only")

      // compute pretty ranks for this result set
      _setPrettyRanks(targetRs, None)

      // Store target result set
      rsStorer.storeResultSet(targetRs, msQueries, storerContext)

      if (saveSpectrumMatch) {
        // Insert target spectrum matches
        logger.info("Storing target spectrum matches...")
        rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)
      }

      return targetRs
    }

  }

  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  private def _insertInstrumentConfig(instrumCfg: InstrumentConfig, context: StorerContext) = {
    SQLMsiSearchWriter.insertInstrumentConfig(instrumCfg, context)
  }

  private def _setPrettyRanks(rs: ResultSet, rsd: Option[ResultSet]) {

    logger.info("Computing pretty ranks")

    if (rsd.isDefined) {
      _computePrettyRanks(rs.peptideMatches ++ rsd.get.peptideMatches, separated = false) // cd
      _computePrettyRanks(rsd.get.peptideMatches, separated = true) // sd
    } else {
      // both pretty ranks will be the same then
      _computePrettyRanks(rs.peptideMatches, separated = false) // cd
    }
    _computePrettyRanks(rs.peptideMatches, separated = true) // sd
  }

  private def _computePrettyRanks(peptideMatches: Array[PeptideMatch], separated: Boolean, scoreTolerance: Float = 0.1f, consecutiveRanks: Boolean = true) {

    val pepMatchesByMsqId = peptideMatches.groupBy(_.msQueryId)

    // Iterate over peptide matches of each MS query
    for ((msqId, pepMatches) <- pepMatchesByMsqId) {

      val sortedPepmatches = pepMatches.sortWith(_.score > _.score)

      /*
       * for one query, with peptide matches ordered by descending score
       * sample data (sequence   score   consecutiveRanking   rankingWithGap) : 
       * KALSCVK   11.26  1   1
       * NLNLFK    8.34   2   2
       * NIINFQ    7.23   3   3
       * NLISSSK   7.23   3   3
       * LNAVFGK   5.27   4   5
       * NINFIK    3.91   5   6
       * NIFNIK    2.83   6   7
       * NLSSAEK   2.83   6   7
       * NLSSSLK   2.83   6   7
       * ARNIFK    2.32   7   10
       */

      var rank = 1
      var refScore = sortedPepmatches(0).score
      var pmNumber = 1 // use this variable to get ranks with gaps (ie. 1-1-3 instead of 1-1-2)

      sortedPepmatches.foreach { pm =>
        // Increase rank if score is too far from reference
        if ((refScore - pm.score) > scoreTolerance) {
          rank = if (consecutiveRanks) rank + 1 else pmNumber
          refScore = pm.score // update reference score        
        }
        if (separated) {
          pm.sdPrettyRank = rank
        } else {
          pm.cdPrettyRank = rank
        }
        pmNumber += 1
      }

    }
  }

  /* Check if a ResultSet is not empty */
  private def checkResultSet(resultSet: ResultSet, decoy: Boolean) {

    val rsType = if (decoy) {
      "Decoy"
    } else {
      "Target"
    }

    if ((resultSet.peptideMatches == null) || (resultSet.peptideMatches.length <= 0)) {
      throw new RuntimeException(rsType + " ResultSet has NO PeptideMatch")
    }

    if ((resultSet.proteinMatches == null) || (resultSet.proteinMatches.length <= 0)) {
      throw new RuntimeException(rsType + " ResultSet has NO ProteinMatch")
    }

  }

}

