package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.SeqDatabase;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class MsiSeqDatabaseRepository {

	private MsiSeqDatabaseRepository() {
	}

	public static SeqDatabase findSeqDatabaseForNameAndFastaAndVersion(
		final EntityManager msiEm,
		final String name,
		final String fastaFilePath) {

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
				throw new NonUniqueResultException(
					"There are more than one SeqDatabases for given name and fastaFilePath");
			}

		}

		return result;
	}

	public static List<Long> findSeqDatabaseIdsForProteinMatch(
		final EntityManager msiEm,
		final long proteinMatchId) {

		JPAUtils.checkEntityManager(msiEm);

		final TypedQuery<Long> query = msiEm.createQuery(
			"select map.id.seqDatabaseId from fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMap map"
				+ " where map.id.proteinMatchId = :proteinMatchId",
			Long.class);
		query.setParameter("proteinMatchId", Long.valueOf(proteinMatchId));

		return query.getResultList();
	}

}
