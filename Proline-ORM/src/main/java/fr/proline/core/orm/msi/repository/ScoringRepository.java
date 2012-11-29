package fr.proline.core.orm.msi.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Scoring;
import fr.proline.core.orm.util.JPARepository;
import fr.proline.util.StringUtils;

public class ScoringRepository extends JPARepository {

    /* Static caches are updated by getScoringIdForType() and getScoreTypeForId() methods */
    /* ScoreType -> Scoring.id , @GuardedBy("CACHE_LOCK") */
    private static final Map<String, Integer> SCORING_IDS_CACHE = new HashMap<String, Integer>();

    /* Scoring.id -> ScoreType , @GuardedBy("CACHE_LOCK") */
    private static final Map<Integer, String> SCORE_TYPES_CACHE = new HashMap<Integer, String>();

    /**
     * Lock object for caches.
     */
    private static final Object CACHE_LOCK = new Object();

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

	synchronized (CACHE_LOCK) {
	    result = SCORING_IDS_CACHE.get(scoreType);

	    if (result == null) {

		final Scoring foundScoring = findScoringForType(scoreType);
		if (foundScoring != null) {
		    result = foundScoring.getId();

		    if (result != null) {
			/* Cache Scoring Id */
			SCORING_IDS_CACHE.put(scoreType, result);

			final String searchEngine = foundScoring.getSearchEngine();
			final String name = foundScoring.getName();

			/* Cache scoreType String */
			if ((searchEngine != null) && (name != null)) {
			    SCORE_TYPES_CACHE.put(result, searchEngine + ':' + name);
			}

		    } // End if (foundScoring.id is not null)

		} // End if (foundScoring is not null)

	    } // End if (scoreType is not in SCORING_IDS_CACHE)

	} // End of synchronized block on CACHE_LOCK

	return result;
    }

    /**
     * Retrives a cached <code>scoreType</code> by given <code>Scoring.id</code>.
     * 
     * @param scoringId
     *            Primary key of <code>Scoring</code> Msi Entity.
     * @return Score type (in domain model) is <code>Scoring.searchEngine + ':' + Scoring.name</code> (
     *         <code>null</code>).
     */
    public String getScoreTypeForId(final Integer scoringId) {

	if (scoringId == null) {
	    throw new IllegalArgumentException("ScoringId is null");
	}

	String result = null;

	synchronized (CACHE_LOCK) {
	    result = SCORE_TYPES_CACHE.get(scoringId);

	    if (result == null) {

		final Scoring foundScoring = getEntityManager().find(Scoring.class, scoringId);
		if (foundScoring != null) {
		    final String searchEngine = foundScoring.getSearchEngine();
		    final String name = foundScoring.getName();

		    if ((searchEngine != null) && (name != null)) {
			final String scoreType = searchEngine + ':' + name;
			/* Cache Scoring Id */
			SCORING_IDS_CACHE.put(scoreType, scoringId);

			/* Cache scoreType String */
			SCORE_TYPES_CACHE.put(scoringId, scoreType);
		    }

		} // End if (foundScoring is not null)

	    } // End if (scoringId is not in SCORE_TYPES_CACHE)

	} // End of synchronized block on CACHE_LOCK

	return result;
    }

}
