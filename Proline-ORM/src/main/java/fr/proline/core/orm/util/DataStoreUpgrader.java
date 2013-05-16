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
     * Upgrade all Proline Databases (UDS, PDI, PS and all projects MSI and LCMS Dbs).
     * 
     * @param connectorFactory
     *            Must be a valid initialized DataStoreConnectorFactory instance.
     */
    public static void upgradeAllDatabases(final IDataStoreConnectorFactory connectorFactory) {

	if ((connectorFactory == null) || !connectorFactory.isInitialized()) {
	    throw new IllegalArgumentException("Invalid connectorFactory");
	}

	/* Upgrade UDS Db */
	final IDatabaseConnector udsDbConnector = connectorFactory.getUdsDbConnector();

	if (udsDbConnector == null) {
	    LOG.warn("DataStoreConnectorFactory has no valid UDS Db connector");
	} else {
	    final int udsDbMigrationCount = DatabaseUpgrader.upgradeDatabase(udsDbConnector);

	    if (udsDbMigrationCount < 0) {
		LOG.warn("Unable to upgrade UDS Db");
	    }

	}

	/* Upgrade PDI Db */
	final IDatabaseConnector pdiDbConnector = connectorFactory.getPdiDbConnector();
	if (pdiDbConnector != null) {
	    final int pdiDbMigrationCount = DatabaseUpgrader.upgradeDatabase(pdiDbConnector);

	    if (pdiDbMigrationCount < 0) {
		LOG.warn("Unable to upgrade PDI Db");
	    }

	}

	/* Upgrade PS Db */
	final IDatabaseConnector psDbConnector = connectorFactory.getPsDbConnector();

	if (psDbConnector == null) {
	    LOG.warn("DataStoreConnectorFactory has no valid PS Db connector");
	} else {
	    final int psDbMigrationCount = DatabaseUpgrader.upgradeDatabase(psDbConnector);

	    if (psDbMigrationCount < 0) {
		LOG.warn("Unable to upgrade PS Db");
	    }

	}

	/* Upgrade all Projects (MSI and LCMS) Dbs */
	if (udsDbConnector != null) {
	    final List<Integer> projectIds = retrieveProjectIds(udsDbConnector);

	    if ((projectIds != null) && !projectIds.isEmpty()) {

		for (final Integer projectId : projectIds) {
		    LOG.debug("Upgrading databases of Project #{}", projectId);

		    final IDatabaseConnector msiDbConnector = connectorFactory.getMsiDbConnector(projectId
			    .intValue());

		    if (msiDbConnector == null) {
			LOG.warn("DataStoreConnectorFactory has no valid MSI Db connector for Project #{}",
				projectId);
		    } else {
			final int msiDbMigrationCount = DatabaseUpgrader.upgradeDatabase(msiDbConnector);

			if (msiDbMigrationCount < 0) {
			    LOG.warn("Unable to upgrade MSI Db of project #{}", projectId);
			}

		    }

		    final IDatabaseConnector lcMsDbConnector = connectorFactory.getLcMsDbConnector(projectId
			    .intValue());
		    if (lcMsDbConnector != null) {
			final int lcMsDbMigrationCount = DatabaseUpgrader.upgradeDatabase(lcMsDbConnector);

			if (lcMsDbMigrationCount < 0) {
			    LOG.warn("Unable to upgrade LCMS Db of Project #{}", projectId);
			}

		    }

		}

	    }

	}

    }

    private static List<Integer> retrieveProjectIds(final IDatabaseConnector udsDbConnector) {
	List<Integer> projectIds = null;

	final EntityManager udsDbEm = udsDbConnector.getEntityManagerFactory().createEntityManager();

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
