package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.SeqDatabase;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class MsiSeqDatabaseRepository extends JPARepository {

    public MsiSeqDatabaseRepository(EntityManager msiEm) {
	super(msiEm);
    }

    public SeqDatabase findSeqDatabaseForNameAndFastaAndVersion(final String name, final String fastaFilePath) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	if (StringUtils.isEmpty(fastaFilePath)) {
	    throw new IllegalArgumentException("Invalid fastaFilePath");
	}

	SeqDatabase result = null;

	TypedQuery<SeqDatabase> query = getEntityManager().createNamedQuery(
		"findMsiSeqDatabaseForNameAndFasta", SeqDatabase.class);
	query.setParameter("name", name);
	query.setParameter("fastaFilePath", fastaFilePath);

	final List<SeqDatabase> seqDatabases = query.getResultList();

	if ((seqDatabases != null) && !seqDatabases.isEmpty()) {

	    if (seqDatabases.size() == 1) {
		result = seqDatabases.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one SeqDatabases for given name and fastaFilePath");
	    }

	}

	return result;
    }

}
