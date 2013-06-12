package fr.proline.core.orm.msi.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.repository.util.JPAUtils;

public final class ProteinSetRepositorty {

    private ProteinSetRepositorty() {
    }

    public static PeptideInstance findPeptideInstanceForPepMatch(final EntityManager msiEm,
	    final long pepMatchID) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideInstance> query = msiEm.createNamedQuery("findPepInstByPepMatch",
		PeptideInstance.class);
	query.setParameter("pmID", Long.valueOf(pepMatchID));
	return query.getSingleResult();
    }

    public static PeptideInstance findPeptideInstanceForPeptide(final EntityManager msiEm, final long pepID) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideInstance> query = msiEm.createNamedQuery("findPepInstForPeptideId",
		PeptideInstance.class);
	query.setParameter("pepID", Long.valueOf(pepID));
	return query.getSingleResult();
    }

}
