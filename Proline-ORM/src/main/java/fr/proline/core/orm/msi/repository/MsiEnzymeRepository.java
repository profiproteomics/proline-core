package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Enzyme;
import fr.proline.core.orm.utils.JPARepository;

public class MsiEnzymeRepository extends JPARepository {

    public MsiEnzymeRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    public Enzyme findEnzymeByName(final String enzymeName) {

	if (enzymeName == null) {
	    throw new IllegalArgumentException("EnzymeName is null");
	}

	Enzyme result = null;

	TypedQuery<Enzyme> query = getEntityManager().createNamedQuery("findMsiEnzymeByName", Enzyme.class);
	query.setParameter("enzymeName", enzymeName.toLowerCase());

	final List<Enzyme> enzimes = query.getResultList();

	if ((enzimes != null) && !enzimes.isEmpty()) {

	    if (enzimes.size() == 1) {
		result = enzimes.get(0);
	    } else {
		throw new RuntimeException("There are more than one Peptide for given name");
	    }

	}

	return result;
    }

}
