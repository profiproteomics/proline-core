package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.JPAUtils;

public final class ExternalDbRepository {

    private ExternalDbRepository() {
    }

    public static ExternalDb findExternalByType(final EntityManager udsEm, final ProlineDatabaseType dbType) {

	JPAUtils.checkEntityManager(udsEm);

	if (dbType == null) {
	    throw new IllegalArgumentException("DbType is null");
	}

	ExternalDb result = null;

	final TypedQuery<ExternalDb> query = udsEm.createNamedQuery("findExternalDbByType", ExternalDb.class);
	query.setParameter("type", dbType);

	final List<ExternalDb> externalDbs = query.getResultList();

	if ((externalDbs != null) && !externalDbs.isEmpty()) {

	    if (externalDbs.size() == 1) {
		result = externalDbs.get(0);
	    } else {
		throw new NonUniqueResultException("There are more than one ExternalDb for given dbType");
	    }

	}

	return result;
    }

    public static ExternalDb findExternalByTypeAndProject(final EntityManager udsEm,
	    final ProlineDatabaseType dbType, final Project project) {

	JPAUtils.checkEntityManager(udsEm);

	if (dbType == null) {
	    throw new IllegalArgumentException("DbType is null");
	}

	if (project == null) {
	    throw new IllegalArgumentException("Project is null");
	}

	ExternalDb result = null;

	final TypedQuery<ExternalDb> query = udsEm.createNamedQuery("findExternalDbByTypeAndProject",
		ExternalDb.class);
	query.setParameter("type", dbType);
	query.setParameter("project", project);

	final List<ExternalDb> externalDbs = query.getResultList();

	if ((externalDbs != null) && !externalDbs.isEmpty()) {

	    if (externalDbs.size() == 1) {
		result = externalDbs.get(0);
	    } else {
		throw new NonUniqueResultException(
			"There are more than one ExternalDb for given dbType and project");
	    }

	}

	return result;
    }

}
