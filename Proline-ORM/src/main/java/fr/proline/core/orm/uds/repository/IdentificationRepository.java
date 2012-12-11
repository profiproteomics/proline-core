package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Identification;
import fr.proline.repository.util.JPAUtils;

public final class IdentificationRepository {

    private IdentificationRepository() {
    }

    public static List<Identification> findIdentificationsByProject(final EntityManager udsEm,
	    final int projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Identification> query = udsEm.createNamedQuery("findIdentificationsByProject",
		Identification.class);
	query.setParameter("id", projectId);
	return query.getResultList();
    }

    public static List<String> findIdentificationNamesByProject(final EntityManager udsEm, final int projectId) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<String> query = udsEm.createNamedQuery("findIdentificationNamesByProject", String.class);
	query.setParameter("id", projectId);
	return query.getResultList();
    }

    public static Identification findIdentificationByName(final EntityManager udsEm, final int projectId,
	    final String name) {

	JPAUtils.checkEntityManager(udsEm);

	TypedQuery<Identification> query = udsEm.createNamedQuery("findIdentificationByName",
		Identification.class);
	query.setParameter("id", projectId).setParameter(":name", name);
	return query.getSingleResult();
    }

}
