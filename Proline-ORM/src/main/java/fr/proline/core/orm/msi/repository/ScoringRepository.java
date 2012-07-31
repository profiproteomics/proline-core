package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Scoring;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class ScoringRepository extends JPARepository {

    public ScoringRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    /**
     * Retrieve Scoring entity by <code>scoreType</code> string.
     * 
     * @param scoreType
     *            Score type (in domain model) is <code>Scoring.searchEngine + ':' + Scoring.name</code> (must
     *            be a non empty <code>String</code>).
     * @return Scoring entity or <code>null</code> if not found
     */
    public Scoring findScoringByType(final String scoreType) {

	if (StringUtils.isEmpty(scoreType)) {
	    throw new IllegalArgumentException("Invalid scoreType");
	}

	Scoring result = null;

	TypedQuery<Scoring> query = getEntityManager().createNamedQuery("findScoringByScoreType",
		Scoring.class);
	query.setParameter("scoreType", scoreType);

	final List<Scoring> scorings = query.getResultList();

	if ((scorings != null) && !scorings.isEmpty()) {

	    if (scorings.size() == 1) {
		result = scorings.get(0);
	    } else {
		throw new RuntimeException("There are more than one Scoring for given scoreType");
	    }

	}

	return result;
    }

}
