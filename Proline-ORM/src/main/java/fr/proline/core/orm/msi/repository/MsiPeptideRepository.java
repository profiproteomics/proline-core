package fr.proline.core.orm.msi.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class MsiPeptideRepository extends JPARepository {

	public MsiPeptideRepository(final EntityManager msiEm) {
		super(msiEm);
	}

	public List<Peptide> findPeptidesForSequence(final String seq) {

		if (StringUtils.isEmpty(seq)) {
			throw new IllegalArgumentException("Invalid seq");
		}

		final TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findMsiPepsForSeq",
				Peptide.class);
		query.setParameter("seq", seq.toUpperCase());

		return query.getResultList();
	}

	/**
	 * Retrieve Msi Peptides by a Collection (List, Set...) of Ids.
	 * 
	 * @param ids
	 *            <code>Collection</code> of Peptide Ids to retrieve (must not be <code>null</code>).
	 * @return List of found Peptides (can be empty if none found).
	 */
	public List<Peptide> findPeptidesForIds(final Collection<Integer> ids) {

		if ((ids == null) || ids.isEmpty()) {
			throw new IllegalArgumentException("Ids collection is empty");
		}
		
		List<Peptide> resultPeps = new ArrayList<Peptide>();
		
		Integer[] idsArray = ids.toArray(new Integer[ids.size()]);				
		
		final TypedQuery<Peptide> query = getEntityManager().createNamedQuery(
				"findMsiPepsForIds", Peptide.class);
		
		for (int index = 0; index < ids.size();) {
			int nextBatchSize = ids.size() - index;
			if (nextBatchSize > BUFFER_SIZE)
				nextBatchSize = BUFFER_SIZE;			
			Integer[] pepsIds = Arrays.copyOfRange(idsArray, index, index + nextBatchSize);
			query.setParameter("ids", Arrays.asList(pepsIds));
			resultPeps.addAll(query.getResultList());
			index = index + nextBatchSize;
		}
		return resultPeps;
	}

	public Peptide findPeptideForSequenceAndPtmStr(final String seq, final String ptmStr) {

		if (StringUtils.isEmpty(seq)) {
			throw new IllegalArgumentException("Invalid seq");
		}

		Peptide result = null;

		TypedQuery<Peptide> query = null;

		if (ptmStr == null) { // Assume NULL <> "" (empty)
			query = getEntityManager().createNamedQuery("findMsiPeptForSeq", Peptide.class);
		} else {
			query = getEntityManager().createNamedQuery("findMsiPeptForSeqAndPtmStr", Peptide.class);
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
