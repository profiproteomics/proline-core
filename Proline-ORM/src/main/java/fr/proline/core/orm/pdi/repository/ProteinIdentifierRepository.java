package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.ProteinIdentifier;
import fr.proline.core.orm.utils.JPARepository;

public class ProteinIdentifierRepository extends JPARepository {

	public ProteinIdentifierRepository(EntityManager em) {
		super(em);
	}

	public ProteinIdentifier findProteinByValueAndTaxon(String value, Integer taxid) {
		TypedQuery<ProteinIdentifier> query = getEntityManager().createNamedQuery("findProteinByValueAndTaxon",
				ProteinIdentifier.class);
		query.setParameter("value", value);
		query.setParameter("taxid", taxid);
		try {
			return query.getSingleResult();
		} catch (NoResultException nre) {
			return null;
		}
	}
}
