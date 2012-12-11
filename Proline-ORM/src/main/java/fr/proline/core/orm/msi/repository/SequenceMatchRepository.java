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
    public static List<SequenceMatch> findSequenceMatchForProteinMatch(final EntityManager msiEm,
	    final int proteinMatchId) {

	JPAUtils.checkEntityManager(msiEm);

	final TypedQuery<SequenceMatch> query = msiEm.createQuery(
		"select sm from fr.proline.core.orm.msi.SequenceMatch sm"
			+ " where sm.id.proteinMatchId = :proteinMatchId", SequenceMatch.class);
	query.setParameter("proteinMatchId", Integer.valueOf(proteinMatchId));

	return query.getResultList();
    }

}
