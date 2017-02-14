package fr.proline.core.orm.pdi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.ProteinIdentifier;
import fr.proline.repository.util.JPAUtils;

public final class ProteinIdentifierRepository {

    private ProteinIdentifierRepository() {
    }

    public static ProteinIdentifier findProteinByValueAndTaxon(final EntityManager pdiEm, final String value,
	    final long taxonId) {

	JPAUtils.checkEntityManager(pdiEm);

	ProteinIdentifier result = null;

	final TypedQuery<ProteinIdentifier> query = pdiEm.createNamedQuery("findProteinByValueAndTaxon",
		ProteinIdentifier.class);
	query.setParameter("value", value);
	query.setParameter("taxid", Long.valueOf(taxonId));

	final List<ProteinIdentifier> proteinIdentifiers = query.getResultList();

	if ((proteinIdentifiers != null) && !proteinIdentifiers.isEmpty()) {

	    if (proteinIdentifiers.size() == 1) {
		result = proteinIdentifiers.get(0);
	    } else {
		throw new NonUniqueResultException(
			"There are more than one ProteinIdentifier for given value and taxonId");
	    }

	}

	return result;
    }

}
