package fr.proline.core.orm.msi.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Scoring;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class ScoringRepository {

    /* Static caches are updated by getScoringIdForType() and getScoreTypeForId() methods */
    /* ScoreType -> Scoring.id , @GuardedBy("CACHE_LOCK") */
    private static final Map<String, Long> SCORING_IDS_CACHE = new HashMap<String, Long>();

    /* Scoring.id -> ScoreType , @GuardedBy("CACHE_LOCK") */
    private static final Map<Long, String> SCORE_TYPES_CACHE = new HashMap<Long, String>();

    /**
     * Lock object for caches.
     */
    private static final Object CACHE_LOCK = new Object();

    private ScoringRepository() {
    }

    /**
     * Retrieves Scoring entity by <code>scoreType</code> string.
     * 
     * @param scoreType
     *            Score type (in domain model) is <code>Scoring.searchEngine + ':' + Scoring.name</code> (must
     *            be a non empty <code>String</code>).
     * @return Scoring entity or <code>null</code> if not found
     */
    public static Scoring findScoringForType(final EntityManager msiEm, final String scoreType) {

	JPAUtils.checkEntityManager(msiEm);

	if (StringUtils.isEmpty(scoreType)) {
	    throw new IllegalArgumentException("Invalid scoreType");
	}

	Scoring result = null;

	final TypedQuery<Scoring> query = msiEm.createNamedQuery("findScoringForScoreType", Scoring.class);
	query.setParameter("scoreType", scoreType);

	final List<Scoring> scorings = query.getResultList();

	if ((scorings != null) && !scorings.isEmpty()) {

	    if (scorings.size() == 1) {
		result = scorings.get(0);
	    } else {
		throw new NonUniqueResultException("There are more than one Scoring for given scoreType");
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
    public static Long getScoringIdForType(final EntityManager msiEm, final String scoreType) {

	JPAUtils.checkEntityManager(msiEm);

	if (StringUtils.isEmpty(scoreType)) {
	    throw new IllegalArgumentException("Invalid scoreType");
	}

	Long result = null;

	synchronized (CACHE_LOCK) {
	    result = SCORING_IDS_CACHE.get(scoreType);

	    if (result == null) {

		final Scoring foundScoring = findScoringForType(msiEm, scoreType);
		if (foundScoring != null) {
		    result = Long.valueOf(foundScoring.getId());

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
    public static String getScoreTypeForId(final EntityManager msiEm, final long scoringId) {

	JPAUtils.checkEntityManager(msiEm);

	final Long scoringIdKey = Long.valueOf(scoringId);

	String result = null;

	synchronized (CACHE_LOCK) {
	    result = SCORE_TYPES_CACHE.get(scoringIdKey);

	    if (result == null) {

		final Scoring foundScoring = msiEm.find(Scoring.class, scoringIdKey);
		if (foundScoring != null) {
		    final String searchEngine = foundScoring.getSearchEngine();
		    final String name = foundScoring.getName();

		    if ((searchEngine != null) && (name != null)) {
			final String scoreType = searchEngine + ':' + name;
			/* Cache Scoring Id */
			SCORING_IDS_CACHE.put(scoreType, scoringIdKey);

			/* Cache scoreType String */
			SCORE_TYPES_CACHE.put(scoringIdKey, scoreType);
			result = scoreType;
		    }

		} // End if (foundScoring is not null)

	    } // End if (scoringId is not in SCORE_TYPES_CACHE)

	} // End of synchronized block on CACHE_LOCK

	return result;
    }

}
