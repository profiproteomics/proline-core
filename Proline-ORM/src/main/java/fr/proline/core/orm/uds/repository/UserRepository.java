package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;

import fr.proline.core.orm.util.JPARepository;

public class UserRepository extends JPARepository {

	protected UserRepository(EntityManager em) {
		super(em);
	}

	
}
