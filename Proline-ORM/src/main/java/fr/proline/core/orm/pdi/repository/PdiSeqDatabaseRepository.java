package fr.proline.core.orm.pdi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.SequenceDbConfig;
import fr.proline.core.orm.pdi.SequenceDbInstance;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class PdiSeqDatabaseRepository extends JPARepository {

    public PdiSeqDatabaseRepository(final EntityManager pdiEm) {
	super(pdiEm);
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
    public SequenceDbInstance findSeqDbInstanceWithNameAndFile(String name, String filePath) {
	try {
	    TypedQuery<SequenceDbInstance> query = getEntityManager().createNamedQuery(
		    "findSeqDBByNameAndFile", SequenceDbInstance.class);
	    query.setParameter("name", name);
	    query.setParameter("filePath", filePath);
	    return query.getSingleResult();
	} catch (NoResultException nre) {
	    return null;
	}
    }

    public SequenceDbConfig findSequenceDbConfigForName(final String name) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	SequenceDbConfig result = null;

	final TypedQuery<SequenceDbConfig> query = getEntityManager().createNamedQuery(
		"findSequenceDbConfigForName", SequenceDbConfig.class);
	query.setParameter("name", name.toLowerCase());

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

}
