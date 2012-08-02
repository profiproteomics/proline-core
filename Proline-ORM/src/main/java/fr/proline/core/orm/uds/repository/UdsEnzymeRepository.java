package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Enzyme;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class UdsEnzymeRepository extends JPARepository {

    public UdsEnzymeRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    public Enzyme findEnzymeByName(final String name) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Enzyme result = null;

	TypedQuery<Enzyme> query = getEntityManager().createNamedQuery("findUdsEnzymeByName", Enzyme.class);
	query.setParameter("name", name.toLowerCase());

	final List<Enzyme> enzymes = query.getResultList();

	if ((enzymes != null) && !enzymes.isEmpty()) {

	    if (enzymes.size() == 1) {
		result = enzymes.get(0);
	    } else {
		throw new RuntimeException("There are more than one Enzyme for given name");
	    }

	}

	return result;
    }

}
