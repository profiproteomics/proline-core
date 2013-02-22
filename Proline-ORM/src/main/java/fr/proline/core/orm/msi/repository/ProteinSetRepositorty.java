package fr.proline.core.orm.msi.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.repository.util.JPAUtils;

public final class ProteinSetRepositorty {

    private ProteinSetRepositorty() {
    }

    public static PeptideInstance findPeptideInstanceForPepMatch(final EntityManager msiEm,
	    final int pepMatchID) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideInstance> query = msiEm.createNamedQuery("findPepInstByPepMatch",
		PeptideInstance.class);
	query.setParameter("pmID", pepMatchID);
	return query.getSingleResult();
    }

    public static PeptideInstance findPeptideInstanceForPeptide(final EntityManager msiEm, final int pepID) {

	JPAUtils.checkEntityManager(msiEm);

	TypedQuery<PeptideInstance> query = msiEm.createNamedQuery("findPepInstForPeptideId",
		PeptideInstance.class);
	query.setParameter("pepID", pepID);
	return query.getSingleResult();
    }

}
