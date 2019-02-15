package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideMatch;
import fr.proline.repository.util.JPAUtils;

public final class PeptideMatchRepository {

	private PeptideMatchRepository() {
	}

	public static List<PeptideMatch> findPeptideMatchByResultSet(
		final EntityManager msiEm,
		final long resultSetId) {

		JPAUtils.checkEntityManager(msiEm);

		TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByResultSet",
			PeptideMatch.class);
		query.setParameter("id", Long.valueOf(resultSetId));
		return query.getResultList();
	}

	public static List<PeptideMatch> findPeptideMatchByPeptide(final EntityManager msiEm, final long peptideId) {

		JPAUtils.checkEntityManager(msiEm);

		TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByPeptide",
			PeptideMatch.class);
		query.setParameter("id", Long.valueOf(peptideId));
		return query.getResultList();
	}

	public static List<PeptideMatch> findPeptideMatchByPeptideAndResultSet(
		final EntityManager msiEm,
		final long peptideId,
		final long resultSetId) {

		JPAUtils.checkEntityManager(msiEm);

		TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByPeptideAndResultSet",
			PeptideMatch.class);
		query.setParameter("peptide_id", Long.valueOf(peptideId));
		query.setParameter("resultset_id", Long.valueOf(resultSetId));
		return query.getResultList();
	}

}
