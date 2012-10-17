package fr.proline.core.orm.ps.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Peptide;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class PsPeptideRepository extends JPARepository {

    public PsPeptideRepository(final EntityManager psEm) {
	super(psEm);
    }

    public List<Peptide> findPeptidesForSequence(final String seq) {

	if (StringUtils.isEmpty(seq)) {
	    throw new IllegalArgumentException("Invalid seq");
	}

	final TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPsPepsForSeq",
		Peptide.class);
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
    public List<Peptide> findPeptidesForIds(final Collection<Integer> ids) {

	if (ids == null) {
	    throw new IllegalArgumentException("Ids collection is null");
	}

	final TypedQuery<Peptide> query = getEntityManager().createNamedQuery("findPsPepsForIds",
		Peptide.class);
	query.setParameter("ids", ids);

	return query.getResultList();
    }

    public Peptide findPeptideForSequenceAndPtmStr(final String seq, final String ptmStr) {

	if (StringUtils.isEmpty(seq)) {
	    throw new IllegalArgumentException("Invalid seq");
	}

	Peptide result = null;

	TypedQuery<Peptide> query = null;

	if (ptmStr == null) { // Assume NULL <> "" (empty)
	    query = getEntityManager().createNamedQuery("findPsPeptForSeqWoPtm", Peptide.class);
	} else {
	    query = getEntityManager().createNamedQuery("findPsPeptForSeqPtmStr", Peptide.class);
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
