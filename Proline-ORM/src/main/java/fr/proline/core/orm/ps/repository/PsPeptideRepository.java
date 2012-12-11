package fr.proline.core.orm.ps.repository;

import static fr.proline.core.orm.util.JPARepositoryConstants.MAX_IN_BATCH_SIZE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Peptide;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class PsPeptideRepository {

    private PsPeptideRepository() {
    }

    public static List<Peptide> findPeptidesForSequence(final EntityManager psEm, final String seq) {

	JPAUtils.checkEntityManager(psEm);

	if (StringUtils.isEmpty(seq)) {
	    throw new IllegalArgumentException("Invalid seq");
	}

	final TypedQuery<Peptide> query = psEm.createNamedQuery("findPsPepsForSeq", Peptide.class);
	query.setParameter("seq", seq.toUpperCase());

	return query.getResultList();
    }

    /**
     * Retrieves Ps Peptides by a Collection (List, Set...) of Ids.
     * 
     * @param ids
     *            <code>Collection</code> of Peptide Ids to retrieve (must not be <code>null</code>).
     * @return List of found Peptides (can be empty if none found).
     */
    public static List<Peptide> findPeptidesForIds(final EntityManager psEm, final Collection<Integer> ids) {

	JPAUtils.checkEntityManager(psEm);

	if ((ids == null) || ids.isEmpty()) {
	    throw new IllegalArgumentException("Ids collection is empty");
	}

	List<Peptide> resultPeps = new ArrayList<Peptide>();

	Integer[] idsArray = ids.toArray(new Integer[ids.size()]);

	final TypedQuery<Peptide> query = psEm.createNamedQuery("findPsPepsForIds", Peptide.class);

	for (int index = 0; index < ids.size();) {
	    int nextBatchSize = ids.size() - index;
	    if (nextBatchSize > MAX_IN_BATCH_SIZE)
		nextBatchSize = MAX_IN_BATCH_SIZE;
	    Integer[] pepsIds = Arrays.copyOfRange(idsArray, index, index + nextBatchSize);
	    query.setParameter("ids", Arrays.asList(pepsIds));
	    resultPeps.addAll(query.getResultList());
	    index = index + nextBatchSize;
	}

	return query.getResultList();
    }

    public static Peptide findPeptideForSequenceAndPtmStr(final EntityManager psEm, final String seq,
	    final String ptmStr) {

	JPAUtils.checkEntityManager(psEm);

	if (StringUtils.isEmpty(seq)) {
	    throw new IllegalArgumentException("Invalid seq");
	}

	Peptide result = null;

	TypedQuery<Peptide> query = null;

	if (ptmStr == null) { // Assume NULL <> "" (empty)
	    query = psEm.createNamedQuery("findPsPeptForSeqWoPtm", Peptide.class);
	} else {
	    query = psEm.createNamedQuery("findPsPeptForSeqPtmStr", Peptide.class);
	    query.setParameter("ptmStr", ptmStr.toUpperCase());
	}

	query.setParameter("seq", seq.toUpperCase()); // In all cases give a Peptide sequence

	final List<Peptide> peptides = query.getResultList();

	if ((peptides != null) && !peptides.isEmpty()) {

	    if (peptides.size() == 1) {
		result = peptides.get(0);
	    } else {
		throw new RuntimeException("There are more than one Peptide for given sequence and ptmString");
	    }

	}

	return result;
    }

}
