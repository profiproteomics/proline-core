package fr.proline.core.orm.utils;

import javax.persistence.EntityManager;

public class JPARepository {

	private EntityManager em;
	
	protected JPARepository(EntityManager em) {
		this.em = em;
	}
	
	public EntityManager getEntityManager() {
		return this.em;
	}

}
