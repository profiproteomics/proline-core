package fr.proline.repository;

public interface IDataStoreConnectorFactory {

	/**
	 * Retrieves the state of this DataStoreConnectorFactory instance.
	 * 
	 * @return <code>true</code> if this DataStoreConnectorFactory is initialized (a DatabaseConnector for a
	 *         valid UDS Db is set).
	 */
	boolean isInitialized();

	IDatabaseConnector getUdsDbConnector();

	IDatabaseConnector getMsiDbConnector(long projectId);

	IDatabaseConnector getLcMsDbConnector(long projectId);

	void closeLcMsDbConnector(long projectId);
	
	void closeMsiDbConnector(long projectId);
	
	void closeProjectConnectors(long projectId);

	void closeAll();

}
