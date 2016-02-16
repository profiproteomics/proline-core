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
			throw new IllegalArgumentException("ContextClassifier is null");
		}

		T result = null;

		if (contextClassifier.isInstance(this)) {
			result = contextClassifier.cast(this);
		} else {
			IExecutionContext wrappedEC = unwrap();

			do {

				if (contextClassifier.isInstance(wrappedEC)) {
					result = contextClassifier.cast(wrappedEC);
				} else if (wrappedEC instanceof DecoratedExecutionContext) {
					wrappedEC = ((DecoratedExecutionContext) wrappedEC).unwrap();
				} else {
					break; // Not found, will return null
				}

			} while (result == null);

		}

		return result;
	}

	public DatabaseConnectionContext getUDSDbConnectionContext() {
		return m_wrappedExecutionContext.getUDSDbConnectionContext();
	}

	public DatabaseConnectionContext getPDIDbConnectionContext() {
		return m_wrappedExecutionContext.getPDIDbConnectionContext();
	}

	public DatabaseConnectionContext getPSDbConnectionContext() {
		return m_wrappedExecutionContext.getPSDbConnectionContext();
	}

	public DatabaseConnectionContext getMSIDbConnectionContext() {
		return m_wrappedExecutionContext.getMSIDbConnectionContext();
	}

	public DatabaseConnectionContext getLCMSDbConnectionContext() {
		return m_wrappedExecutionContext.getLCMSDbConnectionContext();
	}

	public boolean isJPA() {
		return m_wrappedExecutionContext.isJPA();
	}

	/**
	 * Clears this DecoratedContext and closes all included <code>DatabaseConnectionContext</code>.
	 */
	public void closeAll() {

		try {
			clearContext();
		} finally {
			m_wrappedExecutionContext.closeAll();
		}

	}

	/**
	 * Cleans-up this DecoratedContext (clear provider and storer entity caches to release memory).
	 */
	public void clearContext() {
		/* Does nothing by default */
	}

}
