package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.ProteinIdentifier;
import fr.proline.repository.util.JPAUtils;

public final class ProteinIdentifierRepository {

    private ProteinIdentifierRepository() {
    }

    public static ProteinIdentifier findProteinByValueAndTaxon(final EntityManager pdiEm, final String value,
	    final int taxid) {

	JPAUtils.checkEntityManager(pdiEm);

	TypedQuery<ProteinIdentifier> query = pdiEm.createNamedQuery("findProteinByValueAndTaxon",
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
