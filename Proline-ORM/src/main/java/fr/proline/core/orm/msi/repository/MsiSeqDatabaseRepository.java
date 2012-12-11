package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.SeqDatabase;
import fr.proline.repository.util.JPAUtils;
import fr.proline.util.StringUtils;

public final class MsiSeqDatabaseRepository {

    private MsiSeqDatabaseRepository() {
    }

    public static SeqDatabase findSeqDatabaseForNameAndFastaAndVersion(final EntityManager msiEm,
	    final String name, final String fastaFilePath) {

	JPAUtils.checkEntityManager(msiEm);

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	if (StringUtils.isEmpty(fastaFilePath)) {
	    throw new IllegalArgumentException("Invalid fastaFilePath");
	}

	SeqDatabase result = null;

	final TypedQuery<SeqDatabase> query = msiEm.createNamedQuery("findMsiSeqDatabaseForNameAndFasta",
		SeqDatabase.class);
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

    public static List<Integer> findSeqDatabaseIdsForProteinMatch(final EntityManager msiEm,
	    final int proteinMatchId) {

	JPAUtils.checkEntityManager(msiEm);

	final TypedQuery<Integer> query = msiEm.createQuery(
		"select map.id.seqDatabaseId from fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMap map"
			+ " where map.id.proteinMatchId = :proteinMatchId", Integer.class);
	query.setParameter("proteinMatchId", Integer.valueOf(proteinMatchId));

	return query.getResultList();
    }

}
