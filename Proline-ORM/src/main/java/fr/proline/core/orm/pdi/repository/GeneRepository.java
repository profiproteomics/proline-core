package fr.proline.core.orm.pdi.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.BioSequenceGeneMap;
import fr.proline.core.orm.pdi.Gene;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class GeneRepository extends JPARepository {

    public GeneRepository(final EntityManager pdiEm) {
	super(pdiEm);
    }

    public Gene findGeneForNameAndTaxon(final String name, final int taxonId) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Gene result = null;

	final TypedQuery<Gene> query = getEntityManager().createNamedQuery("findGeneForNameAndTaxon",
		Gene.class);
	query.setParameter("name", name.toUpperCase());
	query.setParameter("taxonId", Integer.valueOf(taxonId));

	final List<Gene> genes = query.getResultList();

	if ((genes != null) && !genes.isEmpty()) {

	    if (genes.size() == 1) {
		result = genes.get(0);
	    } else {
		throw new RuntimeException("There are more than one Gene for given name and taxonId");
	    }

	}

	return result;
    }

    /**
     * Retrieves Pdi Genes by a Collection (List, Set...) of names.
     * 
     * @param names
     *            <code>Collection</code> of Gene's names to retrieve (must not be <code>null</code> and names
     *            must be in <strong>upper case</strong>).
     * @return List of found Gene (can be empty if none found), associated Taxon entities are fetched.
     */
    public List<Gene> findGenesForNames(final Collection<String> names) {

	if (names == null) {
	    throw new IllegalArgumentException("Names collection is null");
	}

	final TypedQuery<Gene> query = getEntityManager().createNamedQuery("findGenesForNames", Gene.class);
	query.setParameter("names", names);

	return query.getResultList();
    }

    /**
     * Retrieves Pdi BioSequenceGeneMap by a Collection (List, Set...) of Gene's names.
     * 
     * @param geneNames
     *            <code>Collection</code> of asoociated Gene's names to retrieve (must not be
     *            <code>null</code> and Gene's names must be in <strong>upper case</strong>).
     * @return List of found BioSequenceGeneMap (can be empty if none found), associated BioSequence, Gene and
     *         Taxon entities are fetched.
     */
    public List<BioSequenceGeneMap> findBioSequenceGeneMapsForGeneNames(final Collection<String> geneNames) {

	if (geneNames == null) {
	    throw new IllegalArgumentException("GeneNames collection is null");
	}

	final TypedQuery<BioSequenceGeneMap> query = getEntityManager().createNamedQuery(
		"findBioSequenceGeneMapsForGeneNames", BioSequenceGeneMap.class);
	query.setParameter("geneNames", geneNames);

	return query.getResultList();
    }

}
