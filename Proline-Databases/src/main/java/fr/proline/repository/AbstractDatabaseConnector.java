package fr.proline.repository;

import static fr.profi.util.StringUtils.LINE_SEPARATOR;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import fr.profi.util.PropertiesUtils;
import fr.profi.util.StringUtils;

public abstract class AbstractDatabaseConnector implements IDatabaseConnector, IConnectionListener {

	/* Constants */
	public static final String PERSISTENCE_JDBC_DRIVER_KEY = "javax.persistence.jdbc.driver";
	public static final String PERSISTENCE_JDBC_URL_KEY = "javax.persistence.jdbc.url";
	public static final String PERSISTENCE_JDBC_USER_KEY = "javax.persistence.jdbc.user";
	public static final String PERSISTENCE_JDBC_PASSWORD_KEY = "javax.persistence.jdbc.password";

	public static final String JDBC_APPNAME_KEY = "ApplicationName";
	public static final String HIBERNATE_DIALECT_KEY = "hibernate.dialect";
	public static final String PERSISTENCE_VALIDATION_MODE_KEY = "javax.persistence.validation.mode";

	// Hibernate renamed from c3p0.minPoolSize !
	public static final String HIBERNATE_POOL_MIN_SIZE_KEY = "hibernate.c3p0.min_size";

	// Hibernate renamed from c3p0.maxPoolSize !
	public static final String HIBERNATE_POOL_MAX_SIZE_KEY = "hibernate.c3p0.max_size";

	// Hibernate renamed from c3p0.maxIdleTime !
	public static final String HIBERNATE_POOL_MAX_IDLE_TIME_KEY = "hibernate.c3p0.timeout";
	public static final String HIBERNATE_POOL_MAX_STATEMENTS_PER_CON_KEY = "hibernate.c3p0.maxStatementsPerConnection";
	public static final String HIBERNATE_POOL_TEST_CON_ON_CHECKIN_KEY = "hibernate.c3p0.testConnectionOnCheckin";

	// Hibernate renamed from c3p0.idleConnectionTestPeriod !
	public static final String HIBERNATE_POOL_IDLE_CON_TEST_PERIOD_KEY = "hibernate.c3p0.idle_test_period";
	public static final String HIBERNATE_POOL_PREFERRED_TEST_QUERY_KEY = "hibernate.c3p0.preferredTestQuery";
	public static final String HIBERNATE_FETCH_SIZE_KEY = "hibernate.jdbc.fetch_size";
	public static final String HIBERNATE_BATCH_SIZE_KEY = "hibernate.jdbc.batch_size";
	public static final String HIBERNATE_BATCH_VERSIONED_DATA_KEY = "hibernate.jdbc.batch_versioned_data";
	public static final String HIBERNATE_BYTECODE_OPTIMIZER_KEY = "hibernate.bytecode.use_reflection_optimizer";

	public static final int DEFAULT_MAX_POOL_CONNECTIONS = 20; // TODO increase value for server side
	public static final String PROLINE_MAX_POOL_CONNECTIONS_KEY = "proline.project.max.pool.connection";

	public static final String JDBC_SCHEME = "jdbc";

	private static final Logger LOG = LoggerFactory.getLogger(AbstractDatabaseConnector.class);

	private static final Map<String, Integer> CONNECTOR_INSTANCES = new HashMap<String, Integer>();

	private static final boolean DEFAULT_ORM_OPTIMIZATIONS = true;

	/* Instance variables */
	private final ProlineDatabaseType m_prolineDbType;

	private final Map<Object, Object> m_properties;

	private final String m_ident;

	private final Object m_connectorLock = new Object();

	/* All mutable fields are @GuardedBy("m_connectorLock") */
	private Map<Object, Object> m_additionalProperties;

	protected DataSource m_dataSource;

	private EntityManagerFactory m_entityManagerFactory;

	private List<EntityManager> m_entityManagers;

	private boolean m_closed;

	private int m_connectionCount = 0;

	/* Constructors */
	protected AbstractDatabaseConnector(
		final ProlineDatabaseType prolineDbType,
		final Map<Object, Object> properties) {

		if (prolineDbType == null) {
			throw new IllegalArgumentException("ProlineDbType is null");
		}

		m_prolineDbType = prolineDbType;

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		m_properties = new HashMap<Object, Object>(properties); // Protection copy

		final StringBuilder identBuffer = new StringBuilder(prolineDbType.getPersistenceUnitName());

		final String jdbcURL = PropertiesUtils.getProperty(m_properties, PERSISTENCE_JDBC_URL_KEY);

		if (!StringUtils.isEmpty(jdbcURL)) {
			identBuffer.append('_').append(jdbcURL.replaceAll(":", "_"));
		}

		m_ident = identBuffer.toString();

		final int newConnectorInstancesCount = addConnectorInstances(m_ident);

		if (newConnectorInstancesCount > 1) {
			/* Trace error in Production and Test mode */
			final Exception ex = new Exception("Multiple DatabaseConnector");

			LOG.error("There are " + newConnectorInstancesCount + " DatabaseConnector instances for ["
				+ m_ident + ']', ex);
		}

	}

	/* Public methods */
	public final ProlineDatabaseType getProlineDatabaseType() {
		return m_prolineDbType;
	}

	public final boolean isMemory() {
		/* Protection copy */
		final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

		return isMemory(propertiesCopy);
	}

	public void setAdditionalProperties(final Map<Object, Object> additionalProperties) {

		synchronized (m_connectorLock) {

			if (isClosed()) {
				throw new IllegalStateException("Connector is ALREADY closed");
			}

			if (m_dataSource != null) {
				LOG.warn("DataSource ALREADY created : AdditionalProperties IGNORED");
			}

			if (m_entityManagerFactory != null) {
				LOG.warn("EntityManagerFactory ALREADY created : AdditionalProperties IGNORED");
			}

			if (additionalProperties == null) {
				m_additionalProperties = null;
			} else {
				m_additionalProperties = new HashMap<Object, Object>(additionalProperties); // Protection copy
			}

		}

	}

	public final Object getProperty(Object key) {
		Object result = null;
		synchronized (m_connectorLock) {

			if (m_additionalProperties != null && m_additionalProperties.containsKey(key)) {
				result = m_additionalProperties.get(key);
			} else if (m_properties.containsKey(key)) {
				result = m_properties.get(key);
			}
		}

		return result;
	}

	public final DataSource getDataSource() {

		synchronized (m_connectorLock) {

			if (isClosed()) {
				throw new IllegalStateException("Connector is ALREADY closed");
			}

			if (m_dataSource == null) {
				final ProlineDatabaseType prolineDbType = getProlineDatabaseType();
				/* Protection copy */
				final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

				if ((m_additionalProperties != null) && !m_additionalProperties.isEmpty()) {

					if (LOG.isDebugEnabled()) {
						LOG.debug("Creating DataSource with additionalProperties :" + LINE_SEPARATOR
							+ PropertiesUtils.formatProperties(m_additionalProperties));
					}

					propertiesCopy.putAll(m_additionalProperties);
				}

				try {
					m_dataSource = createDataSource(m_ident, propertiesCopy);
				} catch (Exception ex) {
					/* Log and re-throw */
					final String message = "Error creating DataSource for " + prolineDbType;
					LOG.error(message, ex);

					throw new RuntimeException(message, ex);
				}

			}

		} // End of synchronized block on m_connectorLock

		return m_dataSource;
	}

	protected final EntityManagerFactory getEntityManagerFactory() {

		synchronized (m_connectorLock) {

			if (isClosed()) {
				throw new IllegalStateException("Connector is ALREADY closed");
			}

			if (m_entityManagerFactory == null) {
				m_entityManagers = new ArrayList<EntityManager>();

				final ProlineDatabaseType prolineDbType = getProlineDatabaseType();
				/* Protection copy */
				final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

				if ((m_additionalProperties != null) && !m_additionalProperties.isEmpty()) {

					if (LOG.isDebugEnabled()) {
						LOG.debug("Creating EntityManagerFactory with additionalProperties :"
							+ LINE_SEPARATOR + PropertiesUtils.formatProperties(m_additionalProperties));
					}

					propertiesCopy.putAll(m_additionalProperties);
				}

				/*
				 * Force JDBC Driver, default Hibernate dialect and default ORM optimizations
				 */
				if (propertiesCopy.get(PERSISTENCE_JDBC_DRIVER_KEY) == null) {
					propertiesCopy.put(PERSISTENCE_JDBC_DRIVER_KEY, getDriverType().getJdbcDriver());
				}

				try {
					m_entityManagerFactory = createEntityManagerFactory(
						getProlineDatabaseType(),
						propertiesCopy,
						DEFAULT_ORM_OPTIMIZATIONS);
				} catch (Exception ex) {
					/* Log and re-throw */
					final String message = "Error creating EntityManagerFactory for " + prolineDbType;
					LOG.error(message, ex);

					throw new RuntimeException(message, ex);
				}

			}

		} // End of synchronized block on m_connectorLock

		return m_entityManagerFactory;
	}

	@Override
	public EntityManager createEntityManager() {
		//this.incrementOpenEntityManagerCount();
		EntityManager em = this.getEntityManagerFactory().createEntityManager();

		synchronized (m_connectorLock) {
			m_entityManagers.add(em);
		}

		return em;
	}

	@Override
	public int getOpenEntityManagerCount() {
		int openEmCount = 0;
		for (EntityManager m_entityManager : m_entityManagers) {
			if (m_entityManager.isOpen())
				openEmCount++;
		}
		return openEmCount;
	}

	@Override
	public int getOpenConnectionCount() {
		return m_connectionCount;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ' ' + m_ident;
	}

	public final void close() {

		synchronized (m_connectorLock) {

			if (!m_closed) { // Close only once
				m_closed = true;

				if (m_entityManagerFactory != null) {

					if (this.getOpenEntityManagerCount() > 0) {
						LOG.warn(
							"Some EntityManager instances are still open. " +
								"Closing the database connector before closing all related EntityManager instances may lead to inconsistent behavior.");
					}

					LOG.debug("Closing EntityManagerFactory for [{}]", m_ident);
					try {
						m_entityManagerFactory.close();
					} catch (Exception exClose) {
						LOG.error("Error closing EntityManagerFactory for [" + m_ident + ']', exClose);
					}

				}

				if (m_dataSource != null) {
					doClose(m_ident, m_dataSource);
				}

				final int remainingConnectorInstancesCount = removeConnectorInstances(m_ident);

				if (remainingConnectorInstancesCount > 0) {
					LOG.error("There are {} remaining DatabaseConnector instances for [{}]",
						remainingConnectorInstancesCount, m_ident);
				}

			} // End if (connector is not already closed)

		} // End of synchronized block on m_connectorLock

	}

	public final boolean isClosed() {
		boolean result;

		synchronized (m_connectorLock) {
			result = m_closed;
		} // End of synchronized block on m_connectorLock

		return result;
	}

	protected boolean isMemory(final Map<Object, Object> properties) {
		return false;
	}

	/**
	 * This method is called holding <code>m_connectorLock</code> intrinsic object <strong>lock</strong> by <code>getDataSource</code>.
	 * 
	 * @param database
	 * @param properties
	 * @return
	 */
	protected abstract DataSource createDataSource(final String ident, final Map<Object, Object> properties);

	/**
	 * This method is called holding <code>m_connectorLock</code> intrinsic object <strong>lock</strong> by <code>getEntityManagerFactory</code>.
	 * 
	 * @param database
	 * @param properties
	 * @param ormOptimizations
	 * @return
	 */
	protected EntityManagerFactory createEntityManagerFactory(
		final ProlineDatabaseType prolineDbType,
		final Map<Object, Object> properties,
		final boolean ormOptimizations) {

		if (prolineDbType == null) {
			throw new IllegalArgumentException("Database is null");
		}

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		if (ormOptimizations) {
			optimize(properties);
		}

		if (LOG.isTraceEnabled()) {
			LOG.trace("Effective EntityManagerFactory settings for " + prolineDbType + " :" + LINE_SEPARATOR
				+ PropertiesUtils.formatProperties(properties));
		}

		return Persistence.createEntityManagerFactory(prolineDbType.getPersistenceUnitName(), properties);
	}

	/**
	 * This method is called holding <code>m_connectorLock</code> intrinsic object <strong>lock</strong> by <code>close</code>.
	 * 
	 * @param source
	 */
	protected void doClose(final String ident, final DataSource source) {
		LOG.warn("Closing DatabaseConnector [{}] does not close already retrieved SQL JDBC Connection resources", ident);
	}

	protected static void enableC3P0Pool(final Map<Object, Object> properties) {

		if (properties == null) {
			throw new IllegalArgumentException("Properties Map is null");
		}

		if (properties.get(HIBERNATE_POOL_MIN_SIZE_KEY) == null) {
			/* minPoolSize = 0 */
			properties.put(HIBERNATE_POOL_MIN_SIZE_KEY, "0");
		}

		if (properties.get(HIBERNATE_POOL_MAX_SIZE_KEY) == null) {
			properties.put(HIBERNATE_POOL_MAX_SIZE_KEY, Integer.toString(DEFAULT_MAX_POOL_CONNECTIONS));
		}

		if (properties.get(HIBERNATE_POOL_MAX_IDLE_TIME_KEY) == null) {
			/* Max idle time of 14 minutes */
			properties.put(HIBERNATE_POOL_MAX_IDLE_TIME_KEY, "840");
		}

		if (properties.get(HIBERNATE_POOL_MAX_STATEMENTS_PER_CON_KEY) == null) {
			properties.put(HIBERNATE_POOL_MAX_STATEMENTS_PER_CON_KEY, "30");
		}

		/* If JDBC driver does NOT support Connection.isValid() method, override preferredTestQuery */

		if (properties.get(HIBERNATE_POOL_TEST_CON_ON_CHECKIN_KEY) == null) {
			/* Check connection is valid asynchronously at every connection checkin */
			properties.put(HIBERNATE_POOL_TEST_CON_ON_CHECKIN_KEY, "true");
		}

		if (properties.get(HIBERNATE_POOL_IDLE_CON_TEST_PERIOD_KEY) == null) {
			/* Check pooled but unchecked-out connections every 5 minutes (fastest recommended TCP keepalive) */
			properties.put(HIBERNATE_POOL_IDLE_CON_TEST_PERIOD_KEY, "300");
		}

	}

	private static int addConnectorInstances(final String ident) {
		assert (!StringUtils.isEmpty(ident)) : "addConnectorInstances() invalid ident";

		int result = 0;

		synchronized (CONNECTOR_INSTANCES) {
			final Integer oldCountObj = CONNECTOR_INSTANCES.get(ident);

			int oldCount = 0;

			if (oldCountObj != null) {
				oldCount = oldCountObj.intValue();
			}

			if (oldCount < 0) {
				LOG.error("Inconsistent Connector instances count (adding to {}): {}", ident, oldCount);

				oldCount = 0;
			}

			result = oldCount + 1;

			CONNECTOR_INSTANCES.put(ident, Integer.valueOf(result));
		} // End of synchronized block on CONNECTOR_INSTANCES

		return result;
	}

	private static int removeConnectorInstances(final String ident) {
		assert (!StringUtils.isEmpty(ident)) : "removeConnectorInstances() invalid ident";

		int result = 0;

		synchronized (CONNECTOR_INSTANCES) {
			final Integer oldCountObj = CONNECTOR_INSTANCES.get(ident);

			int oldCount = 0;

			if (oldCountObj != null) {
				oldCount = oldCountObj.intValue();
			}

			if (oldCount <= 0) {
				LOG.error("Inconsistent Connector instances count (removing from {}): {}", ident, oldCount);
			} else {
				result = oldCount - 1;
			}

			CONNECTOR_INSTANCES.put(ident, Integer.valueOf(result));
		} // End of synchronized block on CONNECTOR_INSTANCES

		return result;
	}

	private static void optimize(final Map<Object, Object> properties) {
		assert (properties != null) : "optimize() properties is null";

		if (properties.get(PERSISTENCE_VALIDATION_MODE_KEY) == null) {
			properties.put(PERSISTENCE_VALIDATION_MODE_KEY, "none");
		}

		if (properties.get(HIBERNATE_FETCH_SIZE_KEY) == null) {
			properties.put(HIBERNATE_FETCH_SIZE_KEY, "1000");
		}

		if (properties.get(HIBERNATE_BATCH_SIZE_KEY) == null) {
			properties.put(HIBERNATE_BATCH_SIZE_KEY, "30");
		}

		if (properties.get(HIBERNATE_BATCH_VERSIONED_DATA_KEY) == null) {
			properties.put(HIBERNATE_BATCH_VERSIONED_DATA_KEY, "true");
		}

		if (properties.get(HIBERNATE_BYTECODE_OPTIMIZER_KEY) == null) {
			properties.put(HIBERNATE_BYTECODE_OPTIMIZER_KEY, "true");
		}

	}

	@Override
	public void connectionClosed(ConnectionWrapper connectionWrapper) {
		synchronized (m_connectorLock) {
			m_connectionCount--;
		}
	}

	@Override
	public void connectionCreated(Connection connection) {
		synchronized (m_connectorLock) {
			m_connectionCount++;
		}
	}

}
