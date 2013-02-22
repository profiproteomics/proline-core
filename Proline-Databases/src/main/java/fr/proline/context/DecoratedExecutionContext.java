package fr.proline.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecoratedExecutionContext implements IExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(DecoratedExecutionContext.class);

    private final IExecutionContext m_wrappedExecutionContext;

    protected DecoratedExecutionContext(final IExecutionContext wrappedExecutionContext) {

	if (wrappedExecutionContext == null) {
	    throw new IllegalArgumentException("WrappedExecutionContext is null");
	}

	if (wrappedExecutionContext instanceof DecoratedExecutionContext) {
	    LOG.info(String.format("Wrapping a [%s] into a [%s]", wrappedExecutionContext.getClass()
		    .getName(), getClass().getName()));
	}

	m_wrappedExecutionContext = wrappedExecutionContext;
    }

    public IExecutionContext unwrap() {
	return m_wrappedExecutionContext;
    }

    @Override
    public DatabaseConnectionContext getUDSDbConnectionContext() {
	return m_wrappedExecutionContext.getUDSDbConnectionContext();
    }

    @Override
    public DatabaseConnectionContext getPDIDbConnectionContext() {
	return m_wrappedExecutionContext.getPDIDbConnectionContext();
    }

    @Override
    public DatabaseConnectionContext getPSDbConnectionContext() {
	return m_wrappedExecutionContext.getPSDbConnectionContext();
    }

    @Override
    public DatabaseConnectionContext getMSIDbConnectionContext() {
	return m_wrappedExecutionContext.getMSIDbConnectionContext();
    }

    @Override
    public DatabaseConnectionContext getLCMSDbConnectionContext() {
	return m_wrappedExecutionContext.getLCMSDbConnectionContext();
    }

    @Override
    public boolean isJPA() {
	return m_wrappedExecutionContext.isJPA();
    }

    @Override
    public void closeAll() {
	m_wrappedExecutionContext.closeAll();
    }

}
