package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Project;
import fr.proline.repository.util.JPAUtils;

public final class ProjectRepository {

    private ProjectRepository() {
    }

    public static List<Project> findProjects(final EntityManager udsEm, final int userAccountId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Project> query = udsEm.createNamedQuery("findProjectsByMembership", Project.class);
	query.setParameter("id", userAccountId);
	return query.getResultList();
    }

    public static List<Project> findOwnedProjects(final EntityManager udsEm, final int userAccountId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Project> query = udsEm.createNamedQuery("findProjectsByOwner", Project.class);
	query.setParameter("id", userAccountId);
	return query.getResultList();
    }

    public static List<Integer> findAllProjectIds(final EntityManager udsEm) {

	JPAUtils.checkEntityManager(udsEm);

	final TypedQuery<Integer> query = udsEm.createNamedQuery("findAllProjectIds", Integer.class);

	return query.getResultList();
    }

}
