package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Enzyme;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class UdsEnzymeRepository {

    private UdsEnzymeRepository() {
    }

    public static Enzyme findEnzymeForName(final EntityManager udsEm, final String name) {

	JPAUtils.checkEntityManager(udsEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Enzyme result = null;

	final TypedQuery<Enzyme> query = udsEm.createNamedQuery("findUdsEnzymeForName", Enzyme.class);
	query.setParameter("name", name.toUpperCase());

	final List<Enzyme> enzymes = query.getResultList();

	if ((enzymes != null) && !enzymes.isEmpty()) {

	    if (enzymes.size() == 1) {
		result = enzymes.get(0);
	    } else {
		throw new NonUniqueResultException("There are more than one Enzyme for given name");
	    }

	}

	return result;
    }

}
