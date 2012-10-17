package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Enzyme;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class MsiEnzymeRepository extends JPARepository {

    public MsiEnzymeRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    public Enzyme findEnzymeForName(final String name) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Enzyme result = null;

	final TypedQuery<Enzyme> query = getEntityManager().createNamedQuery("findMsiEnzymeForName",
		Enzyme.class);
	query.setParameter("name", name.toUpperCase());

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
