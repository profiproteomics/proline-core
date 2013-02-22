package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideMatch;
import fr.proline.repository.util.JPAUtils;

public final class PeptideMatchRepository {

    private PeptideMatchRepository() {
    }

    public static List<PeptideMatch> findPeptideMatchByResultSet(final EntityManager msiEm,
	    final int resultSetId) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByResultSet",
		PeptideMatch.class);
	query.setParameter("id", resultSetId);
	return query.getResultList();
    }

    public static List<PeptideMatch> findPeptideMatchByPeptide(final EntityManager msiEm, final int peptideId) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByPeptide",
		PeptideMatch.class);
	query.setParameter("id", peptideId);
	return query.getResultList();
    }

    public static List<PeptideMatch> findPeptideMatchByPeptideAndResultSet(final EntityManager msiEm,
	    final int peptideId, final int resultSetId) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideMatch> query = msiEm.createNamedQuery("findPeptideMatchesByPeptideAndResultSet",
		PeptideMatch.class);
	query.setParameter("peptide_id", peptideId);
	query.setParameter("resultset_id", resultSetId);
	return query.getResultList();
    }

}
