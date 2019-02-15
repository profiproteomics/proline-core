package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.ProteinMatch;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.repository.util.JPAUtils;

public final class ProteinMatchRepository {

	private ProteinMatchRepository() {
	}

	/**
	 * Retrieves <code>ProteinMatch</code> entities by associated <code>ResultSet</code> entity.
	 * 
	 * @param resultSet
	 *            <code>ResultSet</code> entity associated with ProteinMatches (must not be <code>null</code>
	 *            ).
	 * 
	 * @return List of ProteinMatches (can be empty).
	 */
	public static List<ProteinMatch> findProteinMatchesForResultSet(
		final EntityManager msiEm,
		final ResultSet resultSet) {

		JPAUtils.checkEntityManager(msiEm);

		if (resultSet == null) {
			throw new IllegalArgumentException("ResultSet is null");
		}

		final TypedQuery<ProteinMatch> query = msiEm.createNamedQuery("findProteinMatchesForResultSet",
			ProteinMatch.class);
		query.setParameter("resultSet", resultSet);

		return query.getResultList();
	}

	/**
	 * Retrieves <code>ProteinMatch</code> entities by the primary key Id of the associated
	 * <code>ResultSet</code> entity.
	 * 
	 * @param resultSetId
	 *            Primary key of <code>ResultSet</code> entity associated with ProteinMatches.
	 * 
	 * @return List of ProteinMatches (can be empty).
	 */
	public static List<ProteinMatch> findProteinMatchesForResultSetId(
		final EntityManager msiEm,
		final long resultSetId) {

		JPAUtils.checkEntityManager(msiEm);

		final TypedQuery<ProteinMatch> query = msiEm.createNamedQuery("findProteinMatchesForResultSetId",
			ProteinMatch.class);
		query.setParameter("resultSetId", Long.valueOf(resultSetId));

		return query.getResultList();
	}

}
