package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.ProteinMatch;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.util.JPARepository;

public class ProteinMatchRepository extends JPARepository {

    public ProteinMatchRepository(final EntityManager msiEm) {
	super(msiEm);
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
    public List<ProteinMatch> findProteinMatchesForResultSet(final ResultSet resultSet) {

	if (resultSet == null) {
	    throw new IllegalArgumentException("ResultSet is null");
	}

	final TypedQuery<ProteinMatch> query = getEntityManager().createNamedQuery(
		"findProteinMatchesForResultSet", ProteinMatch.class);
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
    public List<ProteinMatch> findProteinMatchesForResultSetId(final int resultSetId) {
	final TypedQuery<ProteinMatch> query = getEntityManager().createNamedQuery(
		"findProteinMatchesForResultSetId", ProteinMatch.class);
	query.setParameter("resultSetId", resultSetId);

	return query.getResultList();
    }

}
