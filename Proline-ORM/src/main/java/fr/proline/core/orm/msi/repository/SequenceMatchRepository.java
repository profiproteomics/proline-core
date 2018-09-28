package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.SequenceMatch;
import fr.proline.repository.util.JPAUtils;

public final class SequenceMatchRepository {

	private SequenceMatchRepository() {
	}

	/**
	 * Retrieves <code>SequenceMatch</code> entities associated with given ProteinMatch.id.
	 * 
	 * @param proteinMatchId
	 *            Primary key of associated ProteinMatch entity.
	 * @return List of associated SequenceMatches, can be empty if none found.
	 */
	public static List<SequenceMatch> findSequenceMatchForProteinMatch(
		final EntityManager msiEm,
		final long proteinMatchId) {

		JPAUtils.checkEntityManager(msiEm);

		final TypedQuery<SequenceMatch> query = msiEm.createQuery(
			"select sm from fr.proline.core.orm.msi.SequenceMatch sm"
				+ " where sm.id.proteinMatchId = :proteinMatchId",
			SequenceMatch.class);
		query.setParameter("proteinMatchId", Long.valueOf(proteinMatchId));

		return query.getResultList();
	}

	/**
	 * Retrieves all  <code>SequenceMatch</code> entities associated to a given resultset
	 * 
	 * @param resultSetId
	 *            ResultSet Id the <code>SequenceMatch</code> belongs to
	 * @return List of associated SequenceMatches, can be empty if none found.
	 */
	public static List<SequenceMatch> findSequenceMatchForResultSet(
		final EntityManager msiEm,
		final long resultSetId) {

		JPAUtils.checkEntityManager(msiEm);

		final TypedQuery<SequenceMatch> query = msiEm.createQuery(
			"select sm from fr.proline.core.orm.msi.SequenceMatch sm"
				+ " where sm.resultSetId = :resultSetId",
			SequenceMatch.class);
		query.setParameter("resultSetId", Long.valueOf(resultSetId));

		return query.getResultList();
	}
}
