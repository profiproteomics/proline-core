package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PtmSpecificity;
import fr.proline.core.orm.utils.JPARepository;

public class PtmSpecificityRepository extends JPARepository {

    public PtmSpecificityRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    public PtmSpecificity f(final String location, final String residue) {

	if (location == null) {
	    throw new IllegalArgumentException("Location is null");
	}

	PtmSpecificity result = null;

	TypedQuery<PtmSpecificity> query = getEntityManager().createNamedQuery("findMsiEnzymeByName",
		PtmSpecificity.class);
	query.setParameter("loc", location.toLowerCase());
	query.setParameter("resid", residue); // TODO : handle null

	final List<PtmSpecificity> specificities = query.getResultList();

	if ((specificities != null) && !specificities.isEmpty()) {

	    if (specificities.size() == 1) {
		result = specificities.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one PtmSpecificity for given location and residue");
	    }

	}

	return result;
    }

}
