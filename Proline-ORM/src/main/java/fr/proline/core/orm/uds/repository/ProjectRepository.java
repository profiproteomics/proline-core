package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.utils.JPARepository;

public class ProjectRepository extends JPARepository {
	
	public ProjectRepository(EntityManager em) {
		super(em);
	}
	
	public List<Project> findProjects(int userAccountId) {
		TypedQuery<Project> query = getEntityManager().createNamedQuery("findProjectsByMembership",Project.class);
		query.setParameter("id", userAccountId);
		return query.getResultList();
	}
	
	public List<Project> findOwnedProjects(int userAccountId) {
		TypedQuery<Project> query = getEntityManager().createNamedQuery("findProjectsByOwner", Project.class);
		query.setParameter("id", userAccountId);
		return query.getResultList();
	}
}
