package fr.proline.context;

public interface IExecutionContext {
	
	/**
	 * The ID of the project associated to this IExecutionContext.
	 */
	long getProjectId();

	/**
	 * Retrieves current connection context for UDS Db.
	 * 
	 * @return UDS DatabaseConnectionContext
	 */
	UdsDbConnectionContext getUDSDbConnectionContext();

	/**
	 * Retrieves current connection context for the MSI Db of the active project.
	 * 
	 * @return MSI DatabaseConnectionContext
	 */
	MsiDbConnectionContext getMSIDbConnectionContext();

	/**
	 * Retrieves current connection context for the LCMS Db of the active project.
	 * 
	 * @return LCMS DatabaseConnectionContext
	 */
	LcMsDbConnectionContext getLCMSDbConnectionContext();

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
