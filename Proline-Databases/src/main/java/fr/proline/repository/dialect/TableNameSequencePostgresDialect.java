package fr.proline.repository.dialect;

import fr.proline.repository.id.TableNameSequenceGenerator;
import fr.proline.repository.id.TableNameSequenceStyleGenerator;
import org.hibernate.dialect.PostgreSQL94Dialect;

/**
 * Creates a sequence per table instead of the default behavior of one global sequence named
 * "hibernate_sequence".
 * <p>
 * To use this custom dialect, set "hibernate.dialect" property to
 * "fr.proline.core.orm.utils.TableNameSequencePostgresDialect".
 * <p>
 * From <a href=
 * "http://grails.1312388.n4.nabble.com/One-hibernate-sequence-is-used-for-all-Postgres-tables-td1351722.html"
 * >http://grails.1312388.n4.nabble.com/One-hibernate-sequence-is-used-for-all-Postgres-tables-td1351722.html
 * </a>
 * <p>
 * And <a
 * href="https://community.jboss.org/wiki/CustomSequences">https://community.jboss.org/wiki/CustomSequences
 * </a>
 * 
 * 
 * @author Burt
 * @author LMN
 */
public class TableNameSequencePostgresDialect extends PostgreSQL94Dialect {

	/**
	 * Get the native identifier generator class.
	 * 
	 * @return TableNameSequenceGenerator.
	 */
//	@Override
//	public Class<?> getNativeIdentifierGeneratorClass() {
//		return TableNameSequenceGenerator.class;
//	}


	@Override
	public String getNativeIdentifierGeneratorStrategy() {
		return TableNameSequenceStyleGenerator.class.getCanonicalName();
	}
}
