package fr.proline.core.orm.ps.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Peptide;
import fr.proline.core.orm.utils.JPARepository;

public class PeptideRepository extends JPARepository { 
	
	public PeptideRepository(EntityManager em) {
		super(em);
	}

	public List<Peptide> findPeptidesBySequence(String seq) {
		TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeq", Peptide.class);
		query.setParameter("seq", seq.toUpperCase());
		return query.getResultList();
	}
	
	public Peptide findPeptidesBySequenceAndPtmStr(String seq, String ptmStr) {
		TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeqPtmStr", Peptide.class);
		query.setParameter("seq", seq.toUpperCase()).setParameter("ptmStr", ptmStr);
		return query.getSingleResult();
	}
	
	public Peptide findPeptidesBySeqWoPtm(String seq) {
		TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeqWoPtm", Peptide.class);
		query.setParameter("seq", seq.toUpperCase());
		return query.getSingleResult();
	}
}
