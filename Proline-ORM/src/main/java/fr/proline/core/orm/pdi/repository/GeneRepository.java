package fr.proline.core.orm.pdi.repository;

import static fr.proline.core.orm.util.JPARepositoryConstants.MAX_IN_BATCH_SIZE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.BioSequenceGeneMap;
import fr.proline.core.orm.pdi.Gene;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class GeneRepository {

    private GeneRepository() {
    }

    public static Gene findGeneForNameAndTaxon(final EntityManager pdiEm, final String name, final int taxonId) {

	JPAUtils.checkEntityManager(pdiEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	Gene result = null;

	final TypedQuery<Gene> query = pdiEm.createNamedQuery("findGeneForNameAndTaxon", Gene.class);
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
    public static List<Gene> findGenesForNames(final EntityManager pdiEm, final Collection<String> names) {

	JPAUtils.checkEntityManager(pdiEm);

	if ((names == null) || names.isEmpty()) {
	    throw new IllegalArgumentException("Names collection is empty");
	}

	List<Gene> resultGenes = new ArrayList<Gene>();

	String[] namesArray = names.toArray(new String[names.size()]);
	final TypedQuery<Gene> query = pdiEm.createNamedQuery("findGenesForNames", Gene.class);

	for (int index = 0; index < names.size();) {
	    int nextBatchSize = names.size() - index;
	    if (nextBatchSize > MAX_IN_BATCH_SIZE)
		nextBatchSize = MAX_IN_BATCH_SIZE;
	    String[] geneNames = Arrays.copyOfRange(namesArray, index, index + nextBatchSize);
	    query.setParameter("names", Arrays.asList(geneNames));
	    resultGenes.addAll(query.getResultList());
	    index = index + nextBatchSize;
	}

	return resultGenes;
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

	if ((genes == null) || genes.isEmpty()) {
	    throw new IllegalArgumentException("Genes collection is empty");
	}

	List<BioSequenceGeneMap> resultBioSeqGeneMaps = new ArrayList<BioSequenceGeneMap>();
	Gene[] namesArray = genes.toArray(new Gene[genes.size()]);

	final TypedQuery<BioSequenceGeneMap> query = pdiEm.createNamedQuery(
		"findBioSequenceGeneMapsForGenes", BioSequenceGeneMap.class);

	for (int index = 0; index < genes.size();) {
	    int nextBatchSize = genes.size() - index;
	    if (nextBatchSize > MAX_IN_BATCH_SIZE)
		nextBatchSize = MAX_IN_BATCH_SIZE;
	    Gene[] bioSeqGeneMaps = Arrays.copyOfRange(namesArray, index, index + nextBatchSize);
	    query.setParameter("genes", Arrays.asList(bioSeqGeneMaps));
	    resultBioSeqGeneMaps.addAll(query.getResultList());
	    index = index + nextBatchSize;
	}

	return resultBioSeqGeneMaps;

    }

}
