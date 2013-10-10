package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideReadablePtmString;
import fr.proline.repository.util.JPAUtils;

public final class PeptideReadablePtmStringRepository {

    private PeptideReadablePtmStringRepository() {
    }

    public static PeptideReadablePtmString findReadablePtmStrForPeptideAndResultSet(
	    final EntityManager msiEm, final long peptideId, final long resultSetId) {

	JPAUtils.checkEntityManager(msiEm);

	PeptideReadablePtmString result = null;

	final TypedQuery<PeptideReadablePtmString> query = msiEm.createNamedQuery(
		"findPeptideReadablePtmStrForPepAndRS", PeptideReadablePtmString.class);
	query.setParameter("peptideId", Long.valueOf(peptideId));
	query.setParameter("resultSetId", Long.valueOf(resultSetId));

	final List<PeptideReadablePtmString> ptmStrings = query.getResultList();

	if ((ptmStrings != null) && !ptmStrings.isEmpty()) {

	    if (ptmStrings.size() == 1) {
		result = ptmStrings.get(0);
	    } else {
		throw new NonUniqueResultException(
			"There are more than one PeptideReadablePtmString for given peptideId and resultSetId");
	    }

	}

	return result;
    }

}
