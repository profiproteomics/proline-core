package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;

import fr.proline.core.orm.utils.JPARepository;

public class UserRepository extends JPARepository {

	protected UserRepository(EntityManager em) {
		super(em);
	}

	
}
