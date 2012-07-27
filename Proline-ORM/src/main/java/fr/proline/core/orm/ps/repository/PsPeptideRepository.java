package fr.proline.core.orm.ps.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Peptide;
import fr.proline.core.orm.utils.JPARepository;

public class PsPeptideRepository extends JPARepository {

    public PsPeptideRepository(final EntityManager em) {
	super(em);
    }

    public List<Peptide> findPeptidesBySequence(final String seq) {

	if (seq == null) {
	    throw new IllegalArgumentException("Seq is null");
	}

	final TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPepsBySeq", Peptide.class);
	query.setParameter("seq", seq.toUpperCase());

	return query.getResultList();
    }

    public Peptide findPeptideBySequenceAndPtmStr(final String seq, final String ptmStr) {

	if (seq == null) {
	    throw new IllegalArgumentException("Seq is null");
	}

	Peptide result = null;

	TypedQuery<Peptide> query = null;

	if ((ptmStr == null) || ptmStr.isEmpty()) {
	    query = getEntityManager().createNamedQuery("findPepsBySeqWoPtm", Peptide.class);
	} else {
	    query = getEntityManager().createNamedQuery("findPepsBySeqPtmStr", Peptide.class);
	    query.setParameter("ptmStr", ptmStr);
	}

	query.setParameter(seq, seq.toUpperCase()); // In all cases give a Peptide sequence

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
