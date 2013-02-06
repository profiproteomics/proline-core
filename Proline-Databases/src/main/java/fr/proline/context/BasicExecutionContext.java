package fr.proline.context;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicExecutionContext implements IExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(BasicExecutionContext.class);

    private final DatabaseConnectionContext m_udsDb;
    private final DatabaseConnectionContext m_pdiDb;
    private final DatabaseConnectionContext m_psDb;
    private final DatabaseConnectionContext m_msiDb;
    private final DatabaseConnectionContext m_lcMsDb;

    public BasicExecutionContext(final DatabaseConnectionContext udsDb,
	    final DatabaseConnectionContext pdiDb, final DatabaseConnectionContext psDb,
	    final DatabaseConnectionContext msiDb, final DatabaseConnectionContext lcMsDb) {

	if (udsDb == null) {
	    LOG.info("UDS DatabaseConnectionContext is null");
	}

	m_udsDb = udsDb;

	if (pdiDb == null) {
	    LOG.info("PDI DatabaseConnectionContext is null");
	}

	m_pdiDb = pdiDb;

	if (psDb == null) {
	    LOG.info("PS DatabaseConnectionContext is null");
	}

	m_psDb = psDb;

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
    public DatabaseConnectionContext getUDSDbConnectionContext() {
	return m_udsDb;
    }

    @Override
    public DatabaseConnectionContext getPDIDbConnectionContext() {
	return m_pdiDb;
    }

    @Override
    public DatabaseConnectionContext getPSDbConnectionContext() {
	return m_psDb;
    }

    @Override
    public DatabaseConnectionContext getMSIDbConnectionContext() {
	return m_msiDb;
    }

    @Override
    public DatabaseConnectionContext getLCMSDbConnectionContext() {
	return m_lcMsDb;
    }

    @Override
    public boolean isJPA() {
	final List<DatabaseConnectionContext> contexts = Arrays.asList(getUDSDbConnectionContext(),
		getPDIDbConnectionContext(), getPSDbConnectionContext(), getMSIDbConnectionContext(),
		getLCMSDbConnectionContext());

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

	if (m_psDb != null) {
	    m_psDb.close();
	}

	if (m_pdiDb != null) {
	    m_pdiDb.close();
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
