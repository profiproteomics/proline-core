package fr.proline.core.orm.utils;

import java.util.Properties;

import org.hibernate.dialect.Dialect;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.SequenceGenerator;
import org.hibernate.type.Type;

public class TableNameSequenceGenerator extends SequenceGenerator {

    /**
     * {@inheritDoc} If the parameters do not contain a {@link SequenceGenerator#SEQUENCE} name, we assign one
     * based on the table and PK column names (using PostgreSQL convention for sequence name :
     * tableName_pkColumnName_seq).
     */
    @Override
    public void configure(final Type type, final Properties params, final Dialect dialect) {

	if (params == null) {
	    throw new IllegalArgumentException("Params is null");
	}

	final String sequenceName = params.getProperty(SEQUENCE);

	if (StringUtils.isEmpty(sequenceName)) {
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
