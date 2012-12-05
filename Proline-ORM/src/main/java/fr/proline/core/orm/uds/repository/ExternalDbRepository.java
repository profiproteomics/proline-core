package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.ExternalDb;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.util.JPARepository;
import fr.proline.repository.Database;

public class ExternalDbRepository extends JPARepository {

    public ExternalDbRepository(final EntityManager udsEm) {
	super(udsEm);
    }

    public ExternalDb findExternalByType(final Database dbType) {

	if (dbType == null) {
	    throw new IllegalArgumentException("DbType is null");
	}

	ExternalDb result = null;

	final TypedQuery<ExternalDb> query = getEntityManager().createNamedQuery("findExternalDbByType",
		ExternalDb.class);
	query.setParameter("type", dbType);

	final List<ExternalDb> externalDbs = query.getResultList();

	if ((externalDbs != null) && !externalDbs.isEmpty()) {

	    if (externalDbs.size() == 1) {
		result = externalDbs.get(0);
	    } else {
		throw new RuntimeException("There are more than one ExternalDb for given dbType");
	    }

	}

	return result;
    }

    public ExternalDb findExternalByTypeAndProject(final Database dbType, final Project project) {

	if (dbType == null) {
	    throw new IllegalArgumentException("DbType is null");
	}

	if (project == null) {
	    throw new IllegalArgumentException("Project is null");
	}

	ExternalDb result = null;

	final TypedQuery<ExternalDb> query = getEntityManager().createNamedQuery(
		"findExternalDbByTypeAndProject", ExternalDb.class);
	query.setParameter("type", dbType);
	query.setParameter("project", project);

	final List<ExternalDb> externalDbs = query.getResultList();

	if ((externalDbs != null) && !externalDbs.isEmpty()) {

	    if (externalDbs.size() == 1) {
		result = externalDbs.get(0);
	    } else {
		throw new RuntimeException("There are more than one ExternalDb for given dbType and project");
	    }

	}

	return result;
    }

}
