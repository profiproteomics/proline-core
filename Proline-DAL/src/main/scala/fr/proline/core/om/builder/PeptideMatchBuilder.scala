package fr.proline.core.om.builder

import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IPeptideProvider

/**
 * @author David Bouyssie
 *
 */
object PeptideMatchBuilder {
  
  protected val PepMatchCols = MsiDbPeptideMatchColumns
  
  def buildPeptideMatches(
    pmRecords: Seq[IValueContainer],
    msQueries: Array[MsQuery],
    scoreTypeById: Map[Long,String],
    peptideProvider: IPeptideProvider
  ): Array[PeptideMatch] = {

    // Load peptides
    val uniqPepIds = pmRecords.map( _.getLong(PepMatchCols.PEPTIDE_ID) ).distinct
    val peptides = peptideProvider.getPeptides(uniqPepIds)

    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => (pep.id -> pep) }
    val msQueryById = Map() ++ msQueries.map { msq => (msq.id -> msq) }

    // Load peptide matches
    val pepMatches = new Array[PeptideMatch](pmRecords.length)

    for (pepMatchIdx <- 0 until pmRecords.length) {

      pepMatches(pepMatchIdx) = this.buildPeptideMatch(
        pmRecords(pepMatchIdx),
        peptideById,
        msQueryById,
        scoreTypeById
      )

    }

    pepMatches
  }
  
  def buildPeptideMatch(
    pepMatchRecord: IValueContainer,
    peptideById: Map[Long,Peptide],
    msQueryById: Map[Long,MsQuery],
    scoreTypeById: Map[Long,String]
  ): PeptideMatch = {
    
    // Retrieve the corresponding peptide
    val pepId = pepMatchRecord.getLong(PepMatchCols.PEPTIDE_ID)
    if ( peptideById.contains(pepId) == false ) {
      throw new Exception("undefined peptide with id ='" + pepId + "' ")
    }
    val peptide = peptideById(pepId)

    // Retrieve the corresponding MS query
    val msQueryOpt = msQueryById.get(pepMatchRecord.getLong(PepMatchCols.MS_QUERY_ID))

    // Retrieve some vars
    val scoreType = scoreTypeById(pepMatchRecord.getLong(PepMatchCols.SCORING_ID))
    
    // Decode JSON properties
    val propertiesAsJsonOpt = pepMatchRecord.getStringOption(PepMatchCols.SERIALIZED_PROPERTIES)
    val properties = propertiesAsJsonOpt.map(ProfiJson.deserialize[PeptideMatchProperties](_))
    
    new PeptideMatch(
      id = pepMatchRecord.getLong(PepMatchCols.ID),
      rank = pepMatchRecord.getInt(PepMatchCols.RANK),
      score = toFloat(pepMatchRecord.getAny(PepMatchCols.SCORE)),
      scoreType = PeptideMatchScoreType.withName(scoreType),
      charge = pepMatchRecord.getInt(PepMatchCols.CHARGE),
      deltaMoz = toFloat( pepMatchRecord.getAny(PepMatchCols.DELTA_MOZ) ),
      isDecoy = pepMatchRecord.getBoolean(PepMatchCols.IS_DECOY),
      peptide = peptide,
      missedCleavage = pepMatchRecord.getInt(PepMatchCols.MISSED_CLEAVAGE),
      fragmentMatchesCount = pepMatchRecord.getInt(PepMatchCols.FRAGMENT_MATCH_COUNT),
      msQuery = msQueryOpt.getOrElse(null),
      resultSetId = pepMatchRecord.getLong(PepMatchCols.RESULT_SET_ID),
      cdPrettyRank = pepMatchRecord.getIntOrElse(PepMatchCols.CD_PRETTY_RANK,0),
      sdPrettyRank = pepMatchRecord.getIntOrElse(PepMatchCols.SD_PRETTY_RANK,0),
      properties = properties
    )
    
  }

}