package fr.proline.core.orm.utils;

import javax.persistence.EntityManager;

public class JPARepository {

    private final EntityManager em;

    protected JPARepository(final EntityManager em) {

	if ((em == null) || !em.isOpen()) {
	    throw new IllegalArgumentException("Invalid EntityManager");
	}

	this.em = em;
    }

    public EntityManager getEntityManager() {
	return this.em;
    }

}
