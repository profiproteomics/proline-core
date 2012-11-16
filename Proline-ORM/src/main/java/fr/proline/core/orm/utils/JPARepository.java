package fr.proline.core.orm.utils;

import javax.persistence.EntityManager;

public class JPARepository {

	protected final int BUFFER_SIZE=999;
	
	private final EntityManager em;

    protected JPARepository(final EntityManager em) {
	JPAUtil.checkEntityManager(em);

	this.em = em;
    }

    public EntityManager getEntityManager() {
	return this.em;
    }

}
