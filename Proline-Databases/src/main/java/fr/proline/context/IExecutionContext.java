package fr.proline.context;

public interface IExecutionContext {

	/**
	 * Retrieves current connection context for UDS Db.
	 * 
	 * @return UDS DatabaseConnectionContext
	 */
	DatabaseConnectionContext getUDSDbConnectionContext();

	/**
	 * Retrieves current connection context for PDI Db.
	 * 
	 * @return PDI DatabaseConnectionContext
	 */
	DatabaseConnectionContext getPDIDbConnectionContext();

	/**
	 * Retrieves current connection context for PS Db.
	 * 
	 * @return PS DatabaseConnectionContext
	 */
	DatabaseConnectionContext getPSDbConnectionContext();

	/**
	 * Retrieves current connection context for the MSI Db of the active project.
	 * 
	 * @return MSI DatabaseConnectionContext
	 */
	DatabaseConnectionContext getMSIDbConnectionContext();

	/**
	 * Retrieves current connection context for the LCMS Db of the active project.
	 * 
	 * @return LCMS DatabaseConnectionContext
	 */
	DatabaseConnectionContext getLCMSDbConnectionContext();

	/**
	 * @return <code>true</code> if all (non-<code>null</code>) DatabaseConnectionContexts are JPA (valid
	 *         EntityManager).
	 */
	boolean isJPA();

	/**
	 * Close all DatabaseConnectionContext (JPA EntityManager an/or SQL JDBC Connection) members.
	 */
	void closeAll();

}
