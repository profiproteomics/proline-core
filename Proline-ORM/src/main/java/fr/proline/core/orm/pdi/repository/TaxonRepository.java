package fr.proline.core.orm.pdi.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.Taxon;
import fr.proline.core.orm.utils.JPARepository;

public class TaxonRepository extends JPARepository {

    public TaxonRepository(final EntityManager pdiEm) {
	super(pdiEm);
    }

    /**
     * Retrieves Taxons by a Collection (List, Set...) of Ids.
     * 
     * @param ids
     *            <code>Collection</code> of Taxon Ids to retrieve (must not be <code>null</code>).
     * @return List of found Taxons (can be empty if none found).
     */
    public List<Taxon> findTaxonsForIds(final Collection<Integer> ids) {

	if ((ids == null) || ids.isEmpty()) {
	    throw new IllegalArgumentException("Ids collection is empty");
	}

	final TypedQuery<Taxon> query = getEntityManager().createNamedQuery("findTaxonsForIds", Taxon.class);
	query.setParameter("ids", ids);

	return query.getResultList();
    }

}
