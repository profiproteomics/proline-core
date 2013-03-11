package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.ProteinIdentifier;
import fr.proline.repository.util.JPAUtils;

public final class ProteinIdentifierRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ProteinIdentifierRepository.class);

    private ProteinIdentifierRepository() {
    }

    public static ProteinIdentifier findProteinByValueAndTaxon(final EntityManager pdiEm, final String value,
	    final int taxid) {

	JPAUtils.checkEntityManager(pdiEm);

	ProteinIdentifier result = null;

	final TypedQuery<ProteinIdentifier> query = pdiEm.createNamedQuery("findProteinByValueAndTaxon",
		ProteinIdentifier.class);
	query.setParameter("value", value);
	query.setParameter("taxid", taxid);

	try {
	    result = query.getSingleResult();
	} catch (NoResultException nrEx) {
	    LOG.info(String.format("No ProteinIdentifier for [%s] and Taxon #%d", value, taxid), nrEx);
	}

	return result;
    }

}
