package fr.proline.core.orm.uds.repository;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.ProjectUserAccountMap;
import fr.proline.repository.util.JPAUtils;

public final class ProjectRepository {

	private ProjectRepository() {
	}

	public static List<Project> findProjects(final EntityManager udsEm, final long userAccountId) {

		JPAUtils.checkEntityManager(udsEm);
		TypedQuery<ProjectUserAccountMap> query = udsEm.createNamedQuery("findProjectUserMapsByMembership", ProjectUserAccountMap.class);
		//	TypedQuery<Project> query = udsEm.createNamedQuery("findProjectsByMembership", Project.class);
		query.setParameter("id", Long.valueOf(userAccountId));
		List<ProjectUserAccountMap> result = query.getResultList();
		List<Project> projects = new ArrayList<Project>(result.size());
		for (ProjectUserAccountMap nextMap : result) {
			projects.add(nextMap.getProject());
		}
		return projects;
	}

	public static List<Project> findOwnedProjects(final EntityManager udsEm, final long userAccountId) {

		JPAUtils.checkEntityManager(udsEm);

		TypedQuery<Project> query = udsEm.createNamedQuery("findProjectsByOwner", Project.class);
		query.setParameter("id", Long.valueOf(userAccountId));
		return query.getResultList();
	}

	public static List<Long> findAllProjectIds(final EntityManager udsEm) {

		JPAUtils.checkEntityManager(udsEm);

		final TypedQuery<Long> query = udsEm.createNamedQuery("findAllProjectIds", Long.class);

		return query.getResultList();
	}
	
	public static List<Long> findAllActiveProjectIds(final EntityManager udsEm) {

		JPAUtils.checkEntityManager(udsEm);

		final TypedQuery<Long> query = udsEm.createNamedQuery("findAllActiveProjectIds", Long.class);

		return query.getResultList();
	}

}
