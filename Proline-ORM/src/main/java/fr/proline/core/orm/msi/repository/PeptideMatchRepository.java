package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideMatch;
import fr.proline.core.orm.utils.JPARepository;

public class PeptideMatchRepository extends JPARepository {

	public PeptideMatchRepository(EntityManager em) {
		super(em);
	}
	
	public List<PeptideMatch> findPeptideMatchByResultSet(int resultSetId) {
		TypedQuery<PeptideMatch> query = getEntityManager().createNamedQuery("findPeptideMatchesByResultSet", PeptideMatch.class);
		query.setParameter("id", resultSetId);
		return query.getResultList();
	}
	
	public List<PeptideMatch> findPeptideMatchByPeptide(int peptideId) {
		TypedQuery<PeptideMatch> query = getEntityManager().createNamedQuery("findPeptideMatchesByPeptide", PeptideMatch.class);
		query.setParameter("id", peptideId);
		return query.getResultList();
	}

	public List<PeptideMatch> findPeptideMatchByPeptideAndResultSet(int peptideId, int resultSetId) {
		TypedQuery<PeptideMatch> query = getEntityManager().createNamedQuery("findPeptideMatchesByPeptideAndResultSet", PeptideMatch.class);
		query.setParameter("peptide_id", peptideId);
		query.setParameter("resultset_id", resultSetId);
		return query.getResultList();
	}

}
