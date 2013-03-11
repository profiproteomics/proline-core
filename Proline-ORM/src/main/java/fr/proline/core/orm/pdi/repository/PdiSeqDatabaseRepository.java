package fr.proline.core.orm.pdi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.SequenceDbConfig;
import fr.proline.core.orm.pdi.SequenceDbInstance;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class PdiSeqDatabaseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PdiSeqDatabaseRepository.class);

    private static final String RESIDUE_COUNT_QUERY = "select sum (bs1.length) from fr.proline.core.orm.pdi.BioSequence bs1 where bs1.id in"
	    + " (select distinct bs2.id from fr.proline.core.orm.pdi.BioSequence bs2, fr.proline.core.orm.pdi.SequenceDbEntry sde"
	    + " where (sde.bioSequence = bs2) and (sde.sequenceDbInstance = :seqDbInstance))";

    private PdiSeqDatabaseRepository() {
    }

    /**
     * Find the PDI SequenceDBInstance with specified name and fasta file path.
     * 
     * @param name
     *            : The name of SequenceDBInstance to search for
     * @param filePath
     *            : Fasta File path of the searched SequenceDBInstance
     * @return SequenceDbInstance with specified name and fasta file path, null if none is found.
     */
    public static SequenceDbInstance findSeqDbInstanceWithNameAndFile(final EntityManager pdiEm,
	    final String name, final String filePath) {

	JPAUtils.checkEntityManager(pdiEm);

	SequenceDbInstance result = null;

	final TypedQuery<SequenceDbInstance> query = pdiEm.createNamedQuery("findSeqDBByNameAndFile",
		SequenceDbInstance.class);
	query.setParameter("name", name);
	query.setParameter("filePath", filePath);

	try {
	    result = query.getSingleResult();
	} catch (NoResultException nrEx) {
	    LOG.info(String.format("No SequenceDbInstance for name [%s] and file [%s]", name, filePath), nrEx);
	}

	return result;
    }

    public static SequenceDbConfig findSequenceDbConfigForName(final EntityManager pdiEm, final String name) {

	JPAUtils.checkEntityManager(pdiEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	SequenceDbConfig result = null;

	final TypedQuery<SequenceDbConfig> query = pdiEm.createNamedQuery("findSequenceDbConfigForName",
		SequenceDbConfig.class);
	query.setParameter("name", name.toUpperCase());

	final List<SequenceDbConfig> seqDbConfigs = query.getResultList();

	if ((seqDbConfigs != null) && !seqDbConfigs.isEmpty()) {

	    if (seqDbConfigs.size() == 1) {
		result = seqDbConfigs.get(0);
	    } else {
		throw new RuntimeException("There are more than one SequenceDbConfig for given name");
	    }

	}

	return result;
    }

    /**
     * Calculate the total residue count (on distinct BioSequences) for a given seqDbInstance.
     * 
     * @param seqDbInstance
     *            The <code>SequenceDbInstance</code> associated with BioSequences via SequenceDbEntries.
     * @return The calculated residue count or <code>-1L</code> if it cannot be calculated.
     */
    public static long calculateResidueCount(final EntityManager pdiEm, final SequenceDbInstance seqDbInstance) {

	JPAUtils.checkEntityManager(pdiEm);

	if (seqDbInstance == null) {
	    throw new IllegalArgumentException("SeqDbInstance is null");
	}

	long result = -1L;

	final Query query = pdiEm.createQuery(RESIDUE_COUNT_QUERY);
	query.setParameter("seqDbInstance", seqDbInstance);

	final List<?> results = query.getResultList();

	if ((results != null) && !results.isEmpty()) {
	    final Object obj = results.get(0);

	    if (obj instanceof Long) {
		result = ((Long) obj).longValue();
	    }

	}

	return result;
    }

}
