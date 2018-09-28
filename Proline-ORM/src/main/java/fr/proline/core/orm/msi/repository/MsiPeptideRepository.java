package fr.proline.core.orm.msi.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class MsiPeptideRepository {

	private MsiPeptideRepository() {
	}

	public static List<Peptide> findPeptidesForSequence(final EntityManager msiEm, final String seq) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(seq)) {
			throw new IllegalArgumentException("Invalid seq");
		}

		final TypedQuery<Peptide> query = msiEm.createNamedQuery("findMsiPepsForSeq", Peptide.class);
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
	public static List<Peptide> findPeptidesForIds(final EntityManager msiEm, final Collection<Long> ids) {

		JPAUtils.checkEntityManager(msiEm);

		return JPARepositoryUtils.executeInQueryAsBatch(
			msiEm.createNamedQuery("findMsiPepsForIds", Peptide.class), "ids", ids);
	}

	public static Peptide findPeptideForSequenceAndPtmStr(
		final EntityManager msiEm,
		final String seq,
		final String ptmStr) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(seq)) {
			throw new IllegalArgumentException("Invalid seq");
		}

		Peptide result = null;

		TypedQuery<Peptide> query = null;

		if (ptmStr == null) { // Assume NULL <> "" (empty)
			query = msiEm.createNamedQuery("findMsiPepsForSeqWOPtm", Peptide.class);
		} else {
			query = msiEm.createNamedQuery("findMsiPeptForSeqAndPtmStr", Peptide.class);
			query.setParameter("ptmStr", ptmStr.toUpperCase());
		}

		query.setParameter("seq", seq.toUpperCase()); // In all cases give a Peptide sequence

		final List<Peptide> peptides = query.getResultList();

		if ((peptides != null) && !peptides.isEmpty()) {

			if (peptides.size() == 1) {
				result = peptides.get(0);
			} else {
				throw new NonUniqueResultException(
					"There are more than one Peptide for given sequence and ptmString"
				);
			}

		}

		return result;
	}

}
