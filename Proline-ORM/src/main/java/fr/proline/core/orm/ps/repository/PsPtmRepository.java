package fr.proline.core.orm.ps.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Ptm;
import fr.proline.core.orm.ps.PtmEvidence;
import fr.proline.core.orm.ps.PtmEvidence.Type;
import fr.proline.core.orm.ps.PtmSpecificity;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class PsPtmRepository extends JPARepository {

    public PsPtmRepository(final EntityManager psEm) {
	super(psEm);
    }

    public Ptm findPtmForName(final String name) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Ptm result = null;

	final TypedQuery<Ptm> query = getEntityManager().createNamedQuery("findPsPtmForName", Ptm.class);
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

    public PtmEvidence findPtmEvidenceByPtmAndType(int ptmId, Type type) {
	TypedQuery<PtmEvidence> query = getEntityManager().createNamedQuery("findPtmEvidenceByPtmAndType",
		PtmEvidence.class);
	query.setParameter("ptm_id", ptmId);
	query.setParameter("type", type.name());
	return query.getSingleResult();
    }

    public PtmSpecificity findPtmSpecificityForNameLocResidu(final String ptmShortName,
	    final String location, final String residu) {

	if (StringUtils.isEmpty(ptmShortName)) {
	    throw new IllegalArgumentException("Invalid ptmShortName");
	}

	if (StringUtils.isEmpty(location)) {
	    throw new IllegalArgumentException("Invalid location");
	}

	PtmSpecificity result = null;

	TypedQuery<PtmSpecificity> query = null;

	if (residu == null) { // Assume NULL <> "" (empty)
	    query = getEntityManager().createNamedQuery("findPsPtmSpecForNameAndLoc", PtmSpecificity.class);

	} else {
	    query = getEntityManager().createNamedQuery("findPsPtmSpecForNameLocResidue",
		    PtmSpecificity.class);

	    query.setParameter("residue", String.valueOf(residu));
	}

	query.setParameter("location", location.toLowerCase());
	query.setParameter("ptmShortName", ptmShortName.toLowerCase());

	final List<PtmSpecificity> ptms = query.getResultList();

	if ((ptms != null) && !ptms.isEmpty()) {

	    if (ptms.size() == 1) {
		result = ptms.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one PtmSpecificity for given name, location and residu");
	    }

	}

	return result;
    }

}
