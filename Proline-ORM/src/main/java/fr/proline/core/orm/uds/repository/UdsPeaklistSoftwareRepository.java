package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.PeaklistSoftware;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class UdsPeaklistSoftwareRepository {

    private UdsPeaklistSoftwareRepository() {
    }

    public static PeaklistSoftware findPeaklistSoftForNameAndVersion(final EntityManager udsEm,
	    final String name, final String version) {

	JPAUtils.checkEntityManager(udsEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	PeaklistSoftware result = null;

	TypedQuery<PeaklistSoftware> query = null;

	if (version == null) { // Assume NULL <> "" (empty)
	    query = udsEm.createNamedQuery("findUdsPeaklistSoftForName", PeaklistSoftware.class);
	} else {
	    query = udsEm.createNamedQuery("findUdsPeaklistSoftForNameAndVersion", PeaklistSoftware.class);
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
