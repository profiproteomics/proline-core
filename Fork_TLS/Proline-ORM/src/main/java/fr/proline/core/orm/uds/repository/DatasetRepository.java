package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Dataset;
import fr.proline.repository.util.JPAUtils;

public final class DatasetRepository {

    private DatasetRepository() {
    }

    public static List<Dataset> findDatasetsByProject(final EntityManager udsEm, final long projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Dataset> query = udsEm.createNamedQuery("findDatasetByProject", Dataset.class);
	query.setParameter("id", Long.valueOf(projectId));
	return query.getResultList();
    }

    public static List<Dataset> findRootDatasetsByProject(final EntityManager udsEm, final long projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Dataset> query = udsEm.createNamedQuery("findRootDatasetByProject", Dataset.class);
	query.setParameter("id", Long.valueOf(projectId));
	return query.getResultList();
    }

    public static List<String> findDatasetNamesByProject(final EntityManager udsEm, final long projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<String> query = udsEm.createNamedQuery("findDatasetNamesByProject", String.class);
	query.setParameter("id", Long.valueOf(projectId));
	return query.getResultList();
    }

    public static List<String> findRootDatasetNamesByProject(final EntityManager udsEm, final long projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<String> query = udsEm.createNamedQuery("findRootDatasetNamesByProject", String.class);
	query.setParameter("id", Long.valueOf(projectId));
	return query.getResultList();
    }

    public static Dataset findDatasetByNameAndProject(final EntityManager udsEm, final long projectId,
	    final String name) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Dataset> query = udsEm.createNamedQuery("findDatasetByNameAndProject", Dataset.class);
	query.setParameter("id", Long.valueOf(projectId));
	query.setParameter("name", name);
	// WARN Throws NoResultException if there is no result
	return query.getSingleResult();
    }

    public static Dataset findRootDatasetByNameAndProject(final EntityManager udsEm, final long projectId,
	    final String name) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Dataset> query = udsEm.createNamedQuery("findRootDatasetByNameAndProject", Dataset.class);
	query.setParameter("id", Long.valueOf(projectId));
	query.setParameter("name", name);
	// WARN Throws NoResultException if there is no result
	return query.getSingleResult();
    }

}
