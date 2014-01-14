package fr.proline.core.orm.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.util.StringUtils;

public final class JPARepositoryUtils {

    /* Constants */
    /**
     * Max IN_EXPRESSION_COUNT_LIMIT of SQLite database drivers.
     */
    public static final int MAX_BATCH_SIZE = 999;

    private static final Logger LOG = LoggerFactory.getLogger(JPARepositoryUtils.class);

    private JPARepositoryUtils() {
    }

    /**
     * Runs a JPA <em>in</em> Query by sub-batch of MAX_BATCH_SIZE. (SQLite has a IN_EXPRESSION_COUNT_LIMIT of
     * 999 to run <em>in collection</em> where clause).
     * 
     * @param query
     *            JPA Query to execute.
     * @param paramName
     *            Name of the collection of parameters to pass to the query.
     * @param paramValues
     *            Collection of parameter values (must not be <code>null</code> or empty).
     * @return List of found entities (can be empty if none found).
     */
    public static <R, P> List<R> executeInQueryAsBatch(final TypedQuery<R> query, final String paramName,
	    final Collection<P> paramValues) {

	if (query == null) {
	    throw new IllegalArgumentException("Query is nul");
	}

	if (StringUtils.isEmpty(paramName)) {
	    throw new IllegalArgumentException("Invalid paramName");
	}

	if ((paramValues == null) || paramValues.isEmpty()) {
	    throw new IllegalArgumentException("Invalid paramValues collection");
	}

	final List<R> result = new ArrayList<R>();

	final List<P> paramValuesList = new ArrayList<P>(paramValues);

	final int totalNParamValues = paramValuesList.size();

	final int nBatches = (totalNParamValues + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;

	for (int batch = 0; batch < nBatches; ++batch) {
	    final List<P> paramsValuesSubList = paramValuesList.subList(batch * MAX_BATCH_SIZE,
		    Math.min((batch + 1) * MAX_BATCH_SIZE, totalNParamValues));

	    if (LOG.isTraceEnabled()) {
		LOG.trace("In Query parameters sub-List size : {}", paramsValuesSubList.size());
	    }

	    query.setParameter(paramName, paramsValuesSubList);

	    result.addAll(query.getResultList());
	}

	return result;
    }

}
