package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Identification;
import fr.proline.core.orm.util.JPARepository;

public class IdentificationRepository extends JPARepository {

	public IdentificationRepository(EntityManager em) {
		super(em);
	}
	
	public List<Identification> findIdentificationsByProject(int projectId) {
		TypedQuery<Identification> query = getEntityManager().createNamedQuery("findIdentificationsByProject", Identification.class);
		query.setParameter("id", projectId);
		return query.getResultList();
	}
	
	public List<String> findIdentificationNamesByProject(int projectId) {
		TypedQuery<String> query = getEntityManager().createNamedQuery("findIdentificationNamesByProject", String.class);
		query.setParameter("id", projectId);
		return query.getResultList();
	}

	public Identification findIdentificationByName(int projectId, String name) {
		TypedQuery<Identification> query = getEntityManager().createNamedQuery("findIdentificationByName", Identification.class);
		query.setParameter("id", projectId).setParameter(":name", name);
		return query.getSingleResult();
	}

}

