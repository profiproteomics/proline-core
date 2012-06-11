package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.BioSequence;
import fr.proline.core.orm.utils.JPARepository;

public class BioSequenceRepository extends JPARepository {

	public BioSequenceRepository(EntityManager em) {
		super(em);
	}

	public BioSequence findBioSequence(String crc, String alphabet, Double mass) {
		TypedQuery<BioSequence> query = getEntityManager().createNamedQuery("findBioSequence", BioSequence.class);
		query.setParameter("mass", mass);
		query.setParameter("crc", crc);
		query.setParameter("alphabet", alphabet);
		try {
			return query.getSingleResult();
		} catch (NoResultException nre) {
			return null;
		}
	}
}
