package fr.proline.repository.id;

import java.util.Properties;

import org.hibernate.dialect.Dialect;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.SequenceGenerator;
import org.hibernate.type.Type;

import fr.profi.util.StringUtils;

/**
 * This class uses one sequence by entity (using PostgreSQL convention for sequence name) instead of global
 * <code>hibernate_sequence</code>.
 * <p>
 * Note : When Hibernate use <code>SequenceHiLoGenerator</code>, generated Ids are
 * <code>nextval(sequence) * maxLo + n</code> which is not compatible with raw SQL insertions.
 * <code>SequenceGenerator</code> works with both SQL and Hibernate insertions.
 * 
 * @author LMN
 * 
 */
public class TableNameSequenceGenerator extends SequenceGenerator {

    /**
     * {@inheritDoc} If the parameters do not contain a {@link org.hibernate.id.SequenceGenerator#SEQUENCE}
     * name, we assign one based on the table and PK column names (using PostgreSQL convention for sequence
     * name : tableName_pkColumnName_seq).
     */
    @Override
    public void configure(final Type type, final Properties params, final Dialect dialect) {

	if (params == null) {
	    throw new IllegalArgumentException("Params is null");
	}

	if (StringUtils.isEmpty(params.getProperty(SEQUENCE))) {
	    final String tableName = params.getProperty(PersistentIdentifierGenerator.TABLE);
	    final String pkColumnName = params.getProperty(PersistentIdentifierGenerator.PK);

	    if (!StringUtils.isEmpty(tableName) && !StringUtils.isEmpty(pkColumnName)) {
		final StringBuilder sb = new StringBuilder();
		sb.append(tableName);
		sb.append('_');
		sb.append(pkColumnName);
		sb.append("_seq");

		params.setProperty(SEQUENCE, sb.toString());
	    }

	}

	super.configure(type, params, dialect);
    }

}
