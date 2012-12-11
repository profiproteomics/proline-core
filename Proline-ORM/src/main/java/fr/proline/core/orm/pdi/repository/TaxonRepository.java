package fr.proline.core.orm.pdi.repository;

import static fr.proline.core.orm.util.JPARepositoryConstants.MAX_IN_BATCH_SIZE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.Taxon;
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

	if ((ids == null) || ids.isEmpty()) {
	    throw new IllegalArgumentException("Ids collection is empty");
	}

	List<Taxon> resultTaxons = new ArrayList<Taxon>();

	Integer[] idsArray = ids.toArray(new Integer[ids.size()]);

	final TypedQuery<Taxon> query = pdiEm.createNamedQuery("findTaxonsForIds", Taxon.class);

	for (int index = 0; index < ids.size();) {
	    int nextBatchSize = ids.size() - index;
	    if (nextBatchSize > MAX_IN_BATCH_SIZE)
		nextBatchSize = MAX_IN_BATCH_SIZE;
	    Integer[] taxonIds = Arrays.copyOfRange(idsArray, index, index + nextBatchSize);
	    query.setParameter("ids", Arrays.asList(taxonIds));
	    resultTaxons.addAll(query.getResultList());
	    index = index + nextBatchSize;
	}

	return resultTaxons;
    }

}
