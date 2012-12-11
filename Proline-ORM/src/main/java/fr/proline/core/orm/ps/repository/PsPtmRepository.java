package fr.proline.core.orm.ps.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Ptm;
import fr.proline.core.orm.ps.PtmClassification;
import fr.proline.core.orm.ps.PtmEvidence;
import fr.proline.core.orm.ps.PtmEvidence.Type;
import fr.proline.core.orm.ps.PtmSpecificity;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class PsPtmRepository {

    private PsPtmRepository() {
    }

    /**
     * Retrieves a <code>Ptm</code> entity from Ps Db by given name (matched ignoring case with
     * <code>shortName</code> or <code>fullName</code>).
     * 
     * @param name
     *            Name (<code>shortName</code> or <code>fullName</code>) of the <code>Ptm</code> entity, must
     *            not be empty.
     * @return Ptm entity or <code>null</code> if not found.
     */
    public static Ptm findPtmForName(final EntityManager psEm, final String name) {

	JPAUtils.checkEntityManager(psEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Ptm result = null;

	final TypedQuery<Ptm> query = psEm.createNamedQuery("findPsPtmForName", Ptm.class);
	query.setParameter("name", name.toUpperCase());

	final List<Ptm> ptms = query.getResultList();

	if ((ptms != null) && !ptms.isEmpty()) {

	    if (ptms.size() == 1) {
		result = ptms.get(0);
	    } else {
		throw new RuntimeException("There are more than one Ptm for given name");
	    }

	}

	return result;
    }

    public static PtmEvidence findPtmEvidenceByPtmAndType(final EntityManager psEm, final int ptmId,
	    final Type type) {

	JPAUtils.checkEntityManager(psEm);

	TypedQuery<PtmEvidence> query = psEm.createNamedQuery("findPtmEvidenceByPtmAndType",
		PtmEvidence.class);
	query.setParameter("ptm_id", ptmId);
	query.setParameter("type", type.name());
	return query.getSingleResult();
    }

    /**
     * Retrieves a <code>PtmSpecificity</code> entity from Ps Db by given name, location and residue.
     * 
     * @param ptmShortName
     *            <code>shortName</code> of the Ptm associated with the <code>PtmSpecificity</code> to
     *            retrieve. Must not be empty, matched ignoring case.
     * @param location
     *            Location of the <code>PtmSpecificity</code>. Must not be empty, matched ignoring case.
     * @param residue
     *            <code>PtmSpecificity</code> residue. Can be <code>null</code> (for C-term or N-term...)
     * @return PtmSpecificity entity or <code>null</code> if not found.
     */
    public static PtmSpecificity findPtmSpecificityForNameLocResidu(final EntityManager psEm,
	    final String ptmShortName, final String location, final String residue) {

	JPAUtils.checkEntityManager(psEm);

	if (StringUtils.isEmpty(ptmShortName)) {
	    throw new IllegalArgumentException("Invalid ptmShortName");
	}

	if (StringUtils.isEmpty(location)) {
	    throw new IllegalArgumentException("Invalid location");
	}

	PtmSpecificity result = null;

	TypedQuery<PtmSpecificity> query = null;

	if (residue == null) { // Assume NULL <> "" (empty)
	    query = psEm.createNamedQuery("findPsPtmSpecForNameAndLoc", PtmSpecificity.class);

	} else {
	    query = psEm.createNamedQuery("findPsPtmSpecForNameLocResidue", PtmSpecificity.class);

	    query.setParameter("residue", String.valueOf(residue));
	}

	query.setParameter("location", location.toUpperCase());
	query.setParameter("ptmShortName", ptmShortName.toUpperCase());

	final List<PtmSpecificity> ptms = query.getResultList();

	if ((ptms != null) && !ptms.isEmpty()) {

	    if (ptms.size() == 1) {
		result = ptms.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one PtmSpecificity for given name, location and residue");
	    }

	}

	return result;
    }

    /**
     * Retrieves a <code>PtmClassification</code> entity from Ps Db by given name (matched ignoring case).
     * 
     * @param name
     *            Name of the <code>PtmClassification</code> entity, must not be empty.
     * @return PtmClassification entity or <code>null</code> if not found.
     */
    public static PtmClassification findPtmClassificationForName(final EntityManager psEm, final String name) {

	JPAUtils.checkEntityManager(psEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	PtmClassification result = null;

	final TypedQuery<PtmClassification> query = psEm.createNamedQuery("findPtmClassificationForName",
		PtmClassification.class);
	query.setParameter("name", name.toUpperCase());

	final List<PtmClassification> classifications = query.getResultList();

	if ((classifications != null) && !classifications.isEmpty()) {

	    if (classifications.size() == 1) {
		result = classifications.get(0);
	    } else {
		throw new RuntimeException("There are more than one PtmClassification for given name");
	    }

	}

	return result;
    }

}
