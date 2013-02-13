package fr.proline.core.orm.pdi.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;

import fr.proline.core.orm.pdi.Taxon;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.util.JPAUtils;

public final class TaxonRepository {

    private TaxonRepository() {
    }

    /**
     * Retrieves Taxons by a Collection (List, Set...) of Ids.
     * 
     * @param ids
     *            <code>Collection</code> of Taxon Ids to retrieve (must not be <code>null</code>).
     * @return List of found Taxons (can be empty if none found).
     */
    public static List<Taxon> findTaxonsForIds(final EntityManager pdiEm, final Collection<Integer> ids) {

	JPAUtils.checkEntityManager(pdiEm);

	return JPARepositoryUtils.executeInQueryAsBatch(
		pdiEm.createNamedQuery("findTaxonsForIds", Taxon.class), "ids", ids);
    }

}
