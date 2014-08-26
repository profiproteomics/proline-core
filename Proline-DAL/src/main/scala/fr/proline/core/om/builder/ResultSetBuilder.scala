package fr.proline.core.om.builder

import fr.profi.util.bytes._
import fr.profi.util.primitives._
import fr.profi.util.serialization._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

/**
 * @author David Bouyssie
 *
 */
object ResultSetBuilder {
  
  protected val RSCols = MsiDbResultSetColumns
  
  def buildResultSet(
    record: IValueContainer,
    msiSearchById: Map[Long,MSISearch],
    msiSearchIdsByParentRsId: Map[Long,Set[Long]],    
    protMatchesByRsId: Map[Long,Array[ProteinMatch]],
    pepMatchesByRsId: Map[Long,Array[PeptideMatch]]
  ): ResultSet = {
    
    val r = record

    val rsId: Long = toLong(r.getAny(RSCols.ID))

    val rsProtMatches = protMatchesByRsId.getOrElse(rsId, Array.empty[ProteinMatch])
    val rsPepMatches = pepMatchesByRsId.getOrElse(rsId, Array.empty[PeptideMatch])
    val rsPeptides = rsPepMatches map { _.peptide } distinct
    val rsType = r.getString(RSCols.TYPE)
    val isDecoy = rsType matches "DECOY_.*"
    val isNative = rsType matches ".*SEARCH"
    val isQuantified = rsType matches "QUANTITATION"
    val decoyRsId = r.getLongOrElse(RSCols.DECOY_RESULT_SET_ID, 0L)

    // Assume child MSI searches if result set is not native
    var( rsMsiSearchId: Long, childMsiSearches: Array[MSISearch] ) = (0L, Array.empty[MSISearch] )
    
    if (isNative) {
      rsMsiSearchId = r.getLongOrElse(RSCols.MSI_SEARCH_ID, 0L)
      childMsiSearches = Array.empty[MSISearch]
    } else if (msiSearchIdsByParentRsId.contains(rsId)) {
      // TODO: return 0 instead of msiSearchIdsByRsId(rsId).head => childMsiSearches is a good alternative
      rsMsiSearchId = msiSearchIdsByParentRsId(rsId).head
      
      val childMsiSearchIds = msiSearchIdsByParentRsId.getOrElse(rsId, Set() ).toArray
      childMsiSearches = childMsiSearchIds.withFilter( msiSearchById.contains(_ )).map( msiSearchById(_) ).sortBy( _.jobNumber )
    }
    
    val msiSearch = msiSearchById.get(rsMsiSearchId)

    // Decode JSON properties
    val propertiesAsJsonOpt = r.getStringOption(RSCols.SERIALIZED_PROPERTIES)
    val properties = propertiesAsJsonOpt.map { ProfiJson.deserialize[ResultSetProperties](_) }

    new ResultSet(
      id = rsId,
      name = r.getString(RSCols.NAME),
      description = r.getStringOrElse(RSCols.DESCRIPTION,null),
      peptides = rsPeptides,
      peptideMatches = rsPepMatches,
      proteinMatches = rsProtMatches,
      isDecoy = isDecoy,
      isNative = isNative,
      isQuantified = isQuantified,
      msiSearch = msiSearch,
      childMsiSearches = childMsiSearches,
      decoyResultSetId = decoyRsId,
      properties = properties
    )

  }

}