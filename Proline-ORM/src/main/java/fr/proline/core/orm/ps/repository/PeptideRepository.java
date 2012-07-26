package fr.proline.core.orm.ps.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.ps.Peptide;
import fr.proline.core.orm.utils.JPARepository;

public class PeptideRepository extends JPARepository {
	
	protected static Logger logger = LoggerFactory.getLogger(PeptideRepository.class);
	
	public PeptideRepository(EntityManager em) {
		super(em);
	}

	public List<Peptide> findPeptidesBySequence(String seq) {
		TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeq", Peptide.class);
		query.setParameter("seq", seq.toUpperCase());
		return query.getResultList();
	}
	
	public Peptide findPeptidesBySequenceAndPtmStr(String seq, String ptmStr) {
		try {
			TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeqPtmStr", Peptide.class);
			query.setParameter("seq", seq.toUpperCase()).setParameter("ptmStr", ptmStr);
			return query.getSingleResult();
		} catch (NoResultException nre) {
			logger.info("No Peptides for sequence {} and PTM String {} ",seq, ptmStr);
			return null;
		}
	}
	
	public Peptide findPeptidesBySeqNoPtm(String seq) {
		try {			
			TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeqWoPtm", Peptide.class);
			query.setParameter("seq", seq.toUpperCase());
			return query.getSingleResult();
		} catch (NoResultException nre) {
			logger.info("No Peptides for sequence {} and no PTMs",seq);
			return null;
		}		
	}
}
