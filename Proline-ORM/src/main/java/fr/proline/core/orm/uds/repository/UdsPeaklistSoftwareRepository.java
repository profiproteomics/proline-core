package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.PeaklistSoftware;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class UdsPeaklistSoftwareRepository extends JPARepository {

    public UdsPeaklistSoftwareRepository(final EntityManager udsEm) {
	super(udsEm);
    }

    public PeaklistSoftware findPeaklistSoftForNameAndVersion(final String name, final String version) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	PeaklistSoftware result = null;

	TypedQuery<PeaklistSoftware> query = null;

	if (version == null) { // Assume NULL <> "" (empty)
	    query = getEntityManager().createNamedQuery("findUdsPeaklistSoftForName", PeaklistSoftware.class);
	} else {
	    query = getEntityManager().createNamedQuery("findUdsPeaklistSoftForNameAndVersion",
		    PeaklistSoftware.class);
	    query.setParameter("version", version.toUpperCase());
	}

	query.setParameter("name", name.toUpperCase()); // In all cases give a Software name

	final List<PeaklistSoftware> softs = query.getResultList();

	if ((softs != null) && !softs.isEmpty()) {

	    if (softs.size() == 1) {
		result = softs.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one PeaklistSoftware for given name and version");
	    }

	}

	return result;
    }

}
