package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.AbstractDatabaseConnector;
import fr.proline.repository.ConnectionMode;
import fr.proline.repository.DriverType;
import fr.proline.repository.ProlineDatabaseType;
import fr.profi.util.StringUtils;

/**
 * The persistent class for the external_db database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findExternalDbByType", query = "select ed from fr.proline.core.orm.uds.ExternalDb ed"
		+ " where ed.type = :type"),

	@NamedQuery(name = "findExternalDbByTypeAndProject", query = "select ed from fr.proline.core.orm.uds.ExternalDb ed"
		+ " join ed.projects as dBProject where (ed.type = :type) and (dBProject = :project)") })
@Table(name = "external_db")
public class ExternalDb implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(ExternalDb.class);

	private static final int URL_BUFFER_SIZE = 256;

	private static final int MAX_PORT = 65535;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Transient
	private DriverType driverType;

	@Column(name = "name")
	private String dbName;

	@Column(name = "version")
	private String dbVersion;

	private String host;

	@Column(name = "is_busy")
	private boolean isBusy;

	private Integer port;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Enumerated(value = EnumType.STRING)
	private ProlineDatabaseType type;

	@Column(name = "connection_mode")
	@Enumerated(value = EnumType.STRING)
	private ConnectionMode connectionMode;

	// bi-directional many-to-many association to Project
	@ManyToMany(mappedBy = "externalDatabases")
	private Set<Project> projects;

	public ExternalDb() {
	}

	public void setId(final long pId) {
		id = pId;
	}

	public long getId() {
		return id;
	}

	public void setDriverType(final DriverType dt) {
		driverType = dt;
	}

	public DriverType getDriverType() {
		return driverType;
	}

	public String getDbName() {
		return this.dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public String getDbVersion() {
		return this.dbVersion;
	}

	public void setDbVersion(String dbVersion) {
		this.dbVersion = dbVersion;
	}

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public boolean getIsBusy() {
		return isBusy;
	}

	public void setIsBusy(final boolean pIsBusy) {
		isBusy = pIsBusy;
	}

	public Integer getPort() {
		return this.port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public ProlineDatabaseType getType() {
		return type;
	}

	public void setType(final ProlineDatabaseType databaseType) {
		type = databaseType;
	}

	public void setProjects(final Set<Project> pProjects) {
		projects = pProjects;
	}

	public Set<Project> getProjects() {
		return this.projects;
	}

	public void addProject(final Project project) {

		if (project != null) {
			Set<Project> localProjects = getProjects();

			if (localProjects == null) {
				localProjects = new HashSet<Project>();

				setProjects(localProjects);
			}

			localProjects.add(project);
		}

	}

	public void removeProject(final Project project) {
		final Set<Project> localProjects = getProjects();

		if (localProjects != null) {
			localProjects.remove(project);
		}

	}

	public ConnectionMode getConnectionMode() {
		return connectionMode;
	}

	public void setConnectionMode(final ConnectionMode connectMode) {
		connectionMode = connectMode;
	}

	/**
	 * Retrieves a standard JDBC properties Map from this <code>ExternalDb</code>.
	 * <p>
	 * Note JDBC URL are built as a basic string, without specific URI rules for character quotation and
	 * encoding.
	 * 
	 * @return Properties Map usable to build a database connector.
	 */
	public Map<Object, Object> toPropertiesMap(String userName, String password) {
		final DriverType dt = getDriverType();

		if (dt == null) {
			throw new IllegalArgumentException("DriverType is null");
		}

		final Map<Object, Object> properties = new HashMap<Object, Object>();

		/* javax.persistence.jdbc.driver */
		properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_DRIVER_KEY, dt.getJdbcDriver());

		/* Build javax.persistence.jdbc.url */
		final StringBuilder urlBuilder = new StringBuilder(URL_BUFFER_SIZE);

		urlBuilder.append(AbstractDatabaseConnector.JDBC_SCHEME).append(':');
		urlBuilder.append(driverType.name().toLowerCase()).append(':');

		final String databasePathname = getDbName();

		switch (getConnectionMode()) {
		case MEMORY:

			if (dt == DriverType.SQLITE) {
				/* Char needed before memory => http://www.sqlite.org/inmemorydb.html */
				urlBuilder.append(":memory:");
			} else {
				urlBuilder.append("mem:");

				if (!StringUtils.isEmpty(databasePathname)) {
					urlBuilder.append(databasePathname);
				}

			}

			break;

		case FILE:

			if (dt != DriverType.SQLITE) {
				urlBuilder.append("file:");
			}

			if (!StringUtils.isEmpty(databasePathname)) {
				urlBuilder.append(databasePathname);
			}

			break;

		case HOST:

			if (dt == DriverType.H2) {
				urlBuilder.append("tcp:");
			}

			urlBuilder.append("//");

			final String serverHostName = getHost();
			if (!StringUtils.isEmpty(serverHostName)) {
				urlBuilder.append(serverHostName);
			}

			final Integer serverPort = getPort();
			if (serverPort != null) {
				final int portValue = serverPort.intValue();

				if ((0 < portValue) && (portValue <= MAX_PORT)) {
					urlBuilder.append(':').append(portValue);
				}
			}

			urlBuilder.append('/');

			if (!StringUtils.isEmpty(databasePathname)) {
				urlBuilder.append(databasePathname);
			}

			break;
		}

		LOG.debug("Database URL [{}]", urlBuilder);

		properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_URL_KEY, urlBuilder.toString());

		// javax.persistence.jdbc.user //
		boolean authent = false;

		if (!StringUtils.isEmpty(userName)) {
			authent = true;
			properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_USER_KEY, userName);
		}

		// javax.persistence.jdbc.password //
		if (StringUtils.isEmpty(password)) {

			if (authent) {
				properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, "");
			}

		} else {
			properties.put(AbstractDatabaseConnector.PERSISTENCE_JDBC_PASSWORD_KEY, password);
		}

		return properties;
	}

	/**
	 * Retrieves a standard JDBC properties Map from this <code>ExternalDb</code>.
	 * 
	 * @param dt
	 *            <code>DriverType</code> (H2, POSTGRESQL, SQLITE).
	 * @return Properties Map usable to build a database connector.
	 */
	public Map<Object, Object> toPropertiesMap(final DriverType dt, String userName, String password) {

		if (dt != null) {
			setDriverType(dt);
		}

		return toPropertiesMap(userName, password);
	}

}
