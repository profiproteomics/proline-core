package fr.proline.core.orm.util;

import javax.persistence.EntityManager;

import fr.proline.repository.util.JPAUtils;

public class JPARepository {

    public static final int BUFFER_SIZE = 999;

    private final EntityManager m_em;

    protected JPARepository(final EntityManager em) {
	JPAUtils.checkEntityManager(em);

	m_em = em;
    }

    public EntityManager getEntityManager() {
	return m_em;
    }

}
