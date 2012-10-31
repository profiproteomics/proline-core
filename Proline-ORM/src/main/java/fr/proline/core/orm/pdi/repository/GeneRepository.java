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
     *            <code>Collection</code> of names of Genes to be retrieved (must not be empty and names must
     *            be in <strong>upper case</strong>).
     * @return List of found Gene (can be empty if none found).
     */
    public List<Gene> findGenesForNames(final Collection<String> names) {

	if ((names == null) || names.isEmpty()) {
	    throw new IllegalArgumentException("Names collection is empty");
	}

	final TypedQuery<Gene> query = getEntityManager().createNamedQuery("findGenesForNames", Gene.class);
	query.setParameter("names", names);

	return query.getResultList();
    }

    /**
     * Retrieves Pdi BioSequenceGeneMap by a Collection (List, Set...) of Gene's names.
     * 
     * @param genes
     *            <code>Collection</code> of associated Gene (must not be empty).
     * @return List of found BioSequenceGeneMap (can be empty if none found).
     */
    public List<BioSequenceGeneMap> findBioSequenceGeneMapsForGenes(final Collection<Gene> genes) {

	if ((genes == null) || genes.isEmpty()) {
	    throw new IllegalArgumentException("Genes collection is empty");
	}

	final TypedQuery<BioSequenceGeneMap> query = getEntityManager().createNamedQuery(
		"findBioSequenceGeneMapsForGenes", BioSequenceGeneMap.class);
	query.setParameter("genes", genes);

	return query.getResultList();
    }

}
