package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeaklistSoftware;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class MsiPeaklistSoftwareRepository {

    private MsiPeaklistSoftwareRepository() {
    }

    public static PeaklistSoftware findPeaklistSoftForNameAndVersion(final EntityManager msiEm,
	    final String name, final String version) {

	JPAUtils.checkEntityManager(msiEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	PeaklistSoftware result = null;

	TypedQuery<PeaklistSoftware> query = null;

	if (version == null) { // Assume NULL <> "" (empty)
	    query = msiEm.createNamedQuery("findMsiPeaklistSoftForName", PeaklistSoftware.class);
	} else {
	    query = msiEm.createNamedQuery("findMsiPeaklistSoftForNameAndVersion", PeaklistSoftware.class);
	    query.setParameter("version", version.toUpperCase());
	}

	query.setParameter("name", name.toUpperCase()); // In all cases give a Software name

	final List<PeaklistSoftware> softs = query.getResultList();

	if ((softs != null) && !softs.isEmpty()) {

	    if (softs.size() == 1) {
		result = softs.get(0);
	    } else {
		throw new NonUniqueResultException(
			"There are more than one PeaklistSoftware for given name and version");
	    }

	}

	return result;
    }

}
