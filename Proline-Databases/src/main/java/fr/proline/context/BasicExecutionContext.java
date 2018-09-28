package fr.proline.context;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicExecutionContext implements IExecutionContext {

	private static final Logger LOG = LoggerFactory.getLogger(BasicExecutionContext.class);

	private final long m_projectId;
	private final UdsDbConnectionContext m_udsDb;
	private final MsiDbConnectionContext m_msiDb;
	private final LcMsDbConnectionContext m_lcMsDb;

	public BasicExecutionContext(
		final long projectId,
		final UdsDbConnectionContext udsDb,
		final MsiDbConnectionContext msiDb,
		final LcMsDbConnectionContext lcMsDb
	) {
		m_projectId = projectId;

		if (udsDb == null) {
			LOG.info("UDS DatabaseConnectionContext is null");
		}

		m_udsDb = udsDb;

		if (msiDb == null) {
			LOG.info("MSI DatabaseConnectionContext is null");
		}

		m_msiDb = msiDb;

		if (lcMsDb == null) {
			LOG.info("LCMS DatabaseConnectionContext is null");
		}

		m_lcMsDb = lcMsDb;
	}
	
	@Override
	public long getProjectId() {
		return m_projectId;
	}

	@Override
	public UdsDbConnectionContext getUDSDbConnectionContext() {
		return m_udsDb;
	}

	@Override
	public MsiDbConnectionContext getMSIDbConnectionContext() {
		return m_msiDb;
	}

	@Override
	public LcMsDbConnectionContext getLCMSDbConnectionContext() {
		return m_lcMsDb;
	}

	@Override
	public boolean isJPA() {
		final List<DatabaseConnectionContext> contexts = Arrays.asList(
			getUDSDbConnectionContext(),
			getMSIDbConnectionContext(),
			getLCMSDbConnectionContext()
		);

		return isAllJPA(contexts);
	}

	@Override
	public void closeAll() {

		if (m_lcMsDb != null) {
			m_lcMsDb.close();
		}

		if (m_msiDb != null) {
			m_msiDb.close();
		}

		if (m_udsDb != null) {
			m_udsDb.close();
		}

	}

	private static boolean isAllJPA(final List<DatabaseConnectionContext> contexts) {
		boolean result = false;

		for (final DatabaseConnectionContext dbContext : contexts) {

			if (dbContext != null) {

				if (dbContext.isJPA()) {
					result = true;
				} else {
					result = false;

					break;
				}

			}

		}

		return result;
	}

}
