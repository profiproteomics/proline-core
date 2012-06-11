package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.Gene;
import fr.proline.core.orm.utils.JPARepository;

public class GeneRepository extends JPARepository {

	public GeneRepository(EntityManager em) {
		super(em);
	}

	public Gene findGeneByNameAndTaxon(String name, Integer taxid) {
		TypedQuery<Gene> query = getEntityManager().createNamedQuery("findGeneByNameAndTaxon", Gene.class);
		query.setParameter("name", name);
		query.setParameter("taxid", taxid);
		try {
			return query.getSingleResult();
		} catch (NoResultException nre) {
			return null;
		}
	}
}
