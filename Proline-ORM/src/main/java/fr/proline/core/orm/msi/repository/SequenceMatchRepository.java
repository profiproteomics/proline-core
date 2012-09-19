package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.SequenceMatch;
import fr.proline.core.orm.utils.JPARepository;

public class SequenceMatchRepository extends JPARepository {

    public SequenceMatchRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    /**
     * Retrieves <code>SequenceMatch</code> entities associated with given ProteinMatch.id.
     * 
     * @param proteinMatchId
     *            Primary key of associated ProteinMatch entity.
     * @return List of associated SequenceMatches, can be empty if none found.
     */
    public List<SequenceMatch> findSequenceMatchForProteinMatch(final int proteinMatchId) {
	final TypedQuery<SequenceMatch> query = getEntityManager().createQuery(
		"select sm from fr.proline.core.orm.msi.SequenceMatch sm"
			+ " where sm.id.proteinMatchId = :proteinMatchId", SequenceMatch.class);
	query.setParameter("proteinMatchId", Integer.valueOf(proteinMatchId));

	return query.getResultList();
    }

}
