package fr.proline.core.orm.msi.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Scoring;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class ScoringRepository extends JPARepository {

    private static final Map<String, Integer> SCORING_IDS_CACHE = new HashMap<String, Integer>();

    public ScoringRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    /**
     * Retrieves Scoring entity by <code>scoreType</code> string.
     * 
     * @param scoreType
     *            Score type (in domain model) is <code>Scoring.searchEngine + ':' + Scoring.name</code> (must
     *            be a non empty <code>String</code>).
     * @return Scoring entity or <code>null</code> if not found
     */
    public Scoring findScoringForType(final String scoreType) {

	if (StringUtils.isEmpty(scoreType)) {
	    throw new IllegalArgumentException("Invalid scoreType");
	}

	Scoring result = null;

	final TypedQuery<Scoring> query = getEntityManager().createNamedQuery("findScoringForScoreType",
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

    /**
     * Retrives a cached Scoring.Id by given <code>scoreType</code>.
     * 
     * @param scoreType
     *            Score type (in domain model) is <code>Scoring.searchEngine + ':' + Scoring.name</code> (must
     *            be a non empty <code>String</code>).
     * @return Scoring.Id or <code>null</code> if not found
     */
    public Integer getScoringIdForType(final String scoreType) {

	if (StringUtils.isEmpty(scoreType)) {
	    throw new IllegalArgumentException("Invalid scoreType");
	}

	Integer result = null;

	synchronized (SCORING_IDS_CACHE) {
	    result = SCORING_IDS_CACHE.get(scoreType);

	    if (result == null) {

		final Scoring foundScoring = findScoringForType(scoreType);
		if (foundScoring != null) {
		    result = foundScoring.getId();

		    if (result != null) {
			SCORING_IDS_CACHE.put(scoreType, result);
		    }

		}

	    }

	} // End of synchronized block on SCORING_IDS_CACHE

	return result;
    }

}
