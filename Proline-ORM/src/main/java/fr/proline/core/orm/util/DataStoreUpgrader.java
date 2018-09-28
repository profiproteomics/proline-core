package fr.proline.core.orm.util;

import java.util.List;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.DatabaseUpgrader;
import fr.proline.repository.IDataStoreConnectorFactory;
import fr.proline.repository.IDatabaseConnector;

public final class DataStoreUpgrader {

	private static final Logger LOG = LoggerFactory.getLogger(DataStoreUpgrader.class);

	/* Private constructor (Utility class) */
	private DataStoreUpgrader() {
	}

	/**
	 * Upgrades all Proline Databases (UDS and all projects MSI and LCMS Dbs).
	 * 
	 * @param connectorFactory
	 *            Must be a valid initialized DataStoreConnectorFactory instance.
	 * @return <code>true</code> if all Databases where successfully upgraded ; <code>false</code> if a Database upgrade failed.
	 */
	public static boolean upgradeAllDatabases(final IDataStoreConnectorFactory connectorFactory, boolean repairChecksum) {

		if ((connectorFactory == null) || !connectorFactory.isInitialized()) {
			throw new IllegalArgumentException("Invalid connectorFactory");
		}

		boolean result = true; // Optimistic initialization

		/* Upgrade UDS Db */
		final IDatabaseConnector udsDbConnector = connectorFactory.getUdsDbConnector();

		if (udsDbConnector == null) {
			LOG.warn("DataStoreConnectorFactory has no valid UDS Db connector");
		} else {
			final int udsDbMigrationCount = DatabaseUpgrader.upgradeDatabase(udsDbConnector, repairChecksum);

			if (udsDbMigrationCount < 0) {
				result = false;
				LOG.warn("Unable to upgrade UDS Db");
			} else {
				LOG.info("UDS Db :" + udsDbMigrationCount + " migration done.");
			}

		}

		/* Upgrade all Projects (MSI and LCMS) Dbs */
		if (udsDbConnector != null) {
			final List<Long> projectIds = retrieveProjectIds(udsDbConnector);

			if ((projectIds != null) && !projectIds.isEmpty()) {

				for (final Long projectId : projectIds) {
					LOG.debug("Upgrading databases of Project #{}", projectId);

					final IDatabaseConnector msiDbConnector = connectorFactory.getMsiDbConnector(projectId.longValue());

					if (msiDbConnector == null) {
						LOG.warn("DataStoreConnectorFactory has no valid MSI Db connector for Project #{}", projectId);
					} else {
						final int msiDbMigrationCount = DatabaseUpgrader.upgradeDatabase(msiDbConnector, repairChecksum);

						if (msiDbMigrationCount < 0) {
							result = false;
							LOG.warn("Unable to upgrade MSI Db of project #{}", projectId);
						} else {
							LOG.info("MSI Db for project " + projectId + " : " + msiDbMigrationCount + " migration done.");
						}

						msiDbConnector.close();
					}

					final IDatabaseConnector lcMsDbConnector = connectorFactory.getLcMsDbConnector(projectId.longValue());

					if (lcMsDbConnector == null) {
						LOG.warn("DataStoreConnectorFactory has no valid LCMS Db connector for Project #{}", projectId);
					} else {
						final int lcMsDbMigrationCount = DatabaseUpgrader.upgradeDatabase(lcMsDbConnector, repairChecksum);

						if (lcMsDbMigrationCount < 0) {
							result = false;
							LOG.warn("Unable to upgrade LCMS Db of Project #{}", projectId);
						} else {
							LOG.info("LCMS Db for project " + projectId + " : " + lcMsDbMigrationCount + " migration done.");
						}

						lcMsDbConnector.close();
					}
				}
			}

			// Close UDSdb connecto at the end
			udsDbConnector.close();
		}

		return result;
	}

	private static List<Long> retrieveProjectIds(final IDatabaseConnector udsDbConnector) {
		List<Long> projectIds = null;

		final EntityManager udsDbEm = udsDbConnector.createEntityManager();

		try {
			projectIds = ProjectRepository.findAllProjectIds(udsDbEm);
		} finally {

			if (udsDbEm != null) {
				try {
					udsDbEm.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS Db EntityManager", exClose);
				}
			}

		}

		return projectIds;
	}

}
