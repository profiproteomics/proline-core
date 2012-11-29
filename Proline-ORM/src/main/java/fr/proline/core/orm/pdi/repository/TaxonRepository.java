package fr.proline.core.orm.pdi.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.core.orm.pdi.Taxon;
import fr.proline.core.orm.util.JPARepository;

public class TaxonRepository extends JPARepository {

	public TaxonRepository(final EntityManager pdiEm) {
		super(pdiEm);
	}

	/**
	 * Retrieves Taxons by a Collection (List, Set...) of Ids.
	 * 
	 * @param ids
	 *          <code>Collection</code> of Taxon Ids to retrieve (must not be
	 *          <code>null</code>).
	 * @return List of found Taxons (can be empty if none found).
	 */
	public List<Taxon> findTaxonsForIds(final Collection<Integer> ids) {

		if ((ids == null) || ids.isEmpty()) {
			throw new IllegalArgumentException("Ids collection is empty");
		}

		List<Taxon> resultTaxons = new ArrayList<Taxon>();
		
		Integer[] idsArray = ids.toArray(new Integer[ids.size()]);				
		
		final TypedQuery<Taxon> query = getEntityManager().createNamedQuery(
				"findTaxonsForIds", Taxon.class);
		
		for (int index = 0; index < ids.size();) {
			int nextBatchSize = ids.size() - index;
			if (nextBatchSize > BUFFER_SIZE)
				nextBatchSize = BUFFER_SIZE;			
			Integer[] taxonIds = Arrays.copyOfRange(idsArray, index, index + nextBatchSize);
			query.setParameter("ids", Arrays.asList(taxonIds));
			resultTaxons.addAll(query.getResultList());
			index = index + nextBatchSize;
		}

		return resultTaxons;
	}

}
