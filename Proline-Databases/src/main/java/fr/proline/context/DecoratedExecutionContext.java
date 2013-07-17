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
	    LOG.info("Wrapping a [{}] into a [{}]", wrappedExecutionContext.getClass().getName(), getClass()
		    .getName());
	}

	m_wrappedExecutionContext = wrappedExecutionContext;
    }

    public IExecutionContext unwrap() {
	return m_wrappedExecutionContext;
    }

    /**
     * Recursively finds a wrapped <em>ExecutionContext</em> of given <code>contextClassifier</code> type.
     * 
     * @param contextClassifier
     *            <em>ExecutionContext</em> type, must not be <code>null</code>
     * @return Wrapped <em>ExecutionContext</em> or <code>null</code> if not found in this
     *         <code>DecoratedExecutionContext</code> hierarchy.
     */
    public <T extends IExecutionContext> T find(final Class<? extends T> contextClassifier) {

	if (contextClassifier == null) {
	    throw new IllegalArgumentException("Type is null");
	}

	T result = null;

	if (contextClassifier.isInstance(this)) {
	    result = contextClassifier.cast(this);
	} else {
	    IExecutionContext wrappedEC = unwrap();

	    recurseFind: do {

		if (contextClassifier.isInstance(wrappedEC)) {
		    result = contextClassifier.cast(wrappedEC);
		} else if (wrappedEC instanceof DecoratedExecutionContext) {
		    wrappedEC = ((DecoratedExecutionContext) wrappedEC).unwrap();
		} else {
		    break recurseFind; // Not found
		}

	    } while (result == null);

	}

	return result;
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
