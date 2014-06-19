package fr.proline.core.orm.pdi.repository;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.BioSequenceGeneMap;
import fr.proline.core.orm.pdi.Gene;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class GeneRepository {

    private GeneRepository() {
    }

    public static Gene findGeneForNameAndTaxon(final EntityManager pdiEm, final String name,
	    final long taxonId) {

	JPAUtils.checkEntityManager(pdiEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Gene result = null;

	final TypedQuery<Gene> query = pdiEm.createNamedQuery("findGeneForNameAndTaxon", Gene.class);
	query.setParameter("name", name.toUpperCase());
	query.setParameter("taxonId", Long.valueOf(taxonId));

	final List<Gene> genes = query.getResultList();

	if ((genes != null) && !genes.isEmpty()) {

	    if (genes.size() == 1) {
		result = genes.get(0);
	    } else {
		throw new NonUniqueResultException("There are more than one Gene for given name and taxonId");
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
    public static List<Gene> findGenesForNames(final EntityManager pdiEm, final Collection<String> names) {

	JPAUtils.checkEntityManager(pdiEm);

	return JPARepositoryUtils.executeInQueryAsBatch(
		pdiEm.createNamedQuery("findGenesForNames", Gene.class), "names", names);
    }

    /**
     * Retrieves Pdi BioSequenceGeneMap by a Collection (List, Set...) of Gene's names.
     * 
     * @param genes
     *            <code>Collection</code> of associated Gene (must not be empty).
     * @return List of found BioSequenceGeneMap (can be empty if none found).
     */
    public static List<BioSequenceGeneMap> findBioSequenceGeneMapsForGenes(final EntityManager pdiEm,
	    final Collection<Gene> genes) {

	JPAUtils.checkEntityManager(pdiEm);

	return JPARepositoryUtils.executeInQueryAsBatch(
		pdiEm.createNamedQuery("findBioSequenceGeneMapsForGenes", BioSequenceGeneMap.class), "genes",
		genes);

    }

}
