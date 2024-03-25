package fr.proline.core.algo.msi

import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.{PeptideMatch, PeptideMatchProperties, ProteinMatch, ResultSet}
import fr.profi.util.regex.RegexUtils._
import fr.profi.util.serialization.ProfiJson

/**
 * @author David Bouyssie
 *
 */
object TargetDecoyResultSetSplitter extends IResultSetSplitter {

  /**
   * Split the provided result set.
   * Two new result sets are created and returned.
   *
   * @param rs the the result set
   * @param acDecoyRegex a regular expression which matches only decoy accession numbers
   * @return a Pair of target/decoy result sets
   */
  def split(rs: ResultSet, acDecoyRegex: util.matching.Regex): Tuple2[ResultSet, ResultSet] = {

    // Partition target/decoy protein matches using the provided regex
    val (decoyProtMatches, targetProtMatches) = rs.proteinMatches.partition { protMatch =>
      protMatch.accession =~ acDecoyRegex
    }

    // Partition target/decoy peptide ids
    val targetPepIdSet = this._getProtMatchesPepIds(targetProtMatches).toSet
    val decoyPepIdSet = this._getProtMatchesPepIds(decoyProtMatches).toSet

    // Partition target/decoy peptide matches
    val targetPepMatches = new ArrayBuffer[PeptideMatch]()
    val decoyPepMatches = new ArrayBuffer[PeptideMatch]()

    for (pepMatch <- rs.peptideMatches) {
      val pepId = pepMatch.peptide.id
      if (targetPepIdSet.contains(pepId)) targetPepMatches += pepMatch
      if (decoyPepIdSet.contains(pepId)) decoyPepMatches += pepMatch
    }

    // Build target and decoy result sets
    val targetRS = this._buildResultSet(rs, targetProtMatches, targetPepMatches, false)
    val decoyRS = this._buildResultSet(rs, decoyProtMatches, decoyPepMatches, true)

    targetRS.decoyResultSet = Some(decoyRS)

    // Return the pair of target/decoy RS
    (targetRS, decoyRS)
  }

  /**
   * Get protein matches peptide ids.
   *
   * @param protMatches the protein matches
   * @return a sequence of peptide ids
   */
  private def _getProtMatchesPepIds(protMatches: Seq[ProteinMatch]): Seq[Long] = {
    for (protMatch <- protMatches; seqMatch <- protMatch.sequenceMatches) yield seqMatch.getPeptideId
  }

  /**
   * Build a result set using an existing one.
   * The first step consists to update the rank of peptide matches.
   * The new result set is built by copying the existing one and
   * by replacing protein matches, peptide matches and peptides.
   *
   * @param tmpRs the provided temporary result set
   * @param protMatches the prot matches
   * @param pepMatches the pep matches
   * @return the fr.proline.core.om.model.msi. result set
   */
  private def _buildResultSet(tmpRs: ResultSet,
                              protMatches: Array[ProteinMatch],
                              pepMatches: Seq[PeptideMatch],
                              isDecoy: Boolean): ResultSet = {

    val newPepMatches = new ArrayBuffer[PeptideMatch]()

    val rsId = ResultSet.generateNewId

    // Re-rank peptide matches
    pepMatches.groupBy(_.msQuery.id).foreach {
      case (msQueryId, msQueryPepMatches) =>
        val sortedPepMatches = msQueryPepMatches.sortBy(_.rank)
        var rank = 1
        for (sortedPepMatch <- sortedPepMatches) {
          //deep clone properties by serialization
          val propertiesAsJson = sortedPepMatch.properties.map(ProfiJson.serialize(_))
          val newProperties = propertiesAsJson.map(ProfiJson.deserialize[PeptideMatchProperties](_))
          val newRankedPepMatch = sortedPepMatch.copy(id = PeptideMatch.generateNewId, rank = rank, isDecoy = isDecoy, resultSetId = rsId, properties = newProperties)
          newPepMatches += newRankedPepMatch
          rank += 1
        }
    }

    val peptideByIds = newPepMatches.groupBy(_.peptide.id)

    val newProtMatches = protMatches.map { protMatch =>
      val seqMatches = protMatch.sequenceMatches.filter { seqMatch => peptideByIds.contains(seqMatch.getPeptideId) }

      val newSeqMatch = seqMatches.map { seqMatch =>
        val pepMatches = peptideByIds.get(seqMatch.getPeptideId).getOrElse(List())
        var bestPepMatch = pepMatches(0)
        for (i <- 1 until pepMatches.length) {
          val nextPepMatch = pepMatches(i)
          if ((bestPepMatch.score < nextPepMatch.score) || ((bestPepMatch.score == nextPepMatch.score) && (bestPepMatch.id < nextPepMatch.id)))
            bestPepMatch = nextPepMatch
        }

        seqMatch.copy(resultSetId = rsId, bestPeptideMatch = Some(bestPepMatch), isDecoy = isDecoy)
      }

      protMatch.copy(isDecoy = isDecoy, resultSetId = rsId, sequenceMatches = newSeqMatch)
    }

    // Build the result set: create a copy of the TMP one and replace some attributes
    tmpRs.copy(
      id = rsId,
      peptides = newPepMatches.map(_.peptide).distinct.toArray,
      peptideMatches = newPepMatches.toArray,
      proteinMatches = newProtMatches,
      isDecoy = isDecoy,
      decoyResultSetId = 0,
      decoyResultSet = None
    )
  }

}