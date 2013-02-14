package fr.proline.repository.util;

import org.hibernate.jdbc.ReturningWork;

/* ReturningWork interface is specific to Hibernate ORM */
public interface JDBCReturningWork<T> extends ReturningWork<T> {

}
