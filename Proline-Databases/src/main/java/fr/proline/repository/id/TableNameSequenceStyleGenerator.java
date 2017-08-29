package fr.proline.repository.id;


import fr.profi.util.StringUtils;
import org.hibernate.MappingException;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.util.Properties;

public class TableNameSequenceStyleGenerator extends SequenceStyleGenerator {

  @Override
  public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException {

    if (params == null) {
      throw new IllegalArgumentException("Params is null");
    }

    if (StringUtils.isEmpty(params.getProperty(SEQUENCE_PARAM))) {
      final String tableName = params.getProperty(PersistentIdentifierGenerator.TABLE);
      final String pkColumnName = params.getProperty(PersistentIdentifierGenerator.PK);

      if (!StringUtils.isEmpty(tableName) && !StringUtils.isEmpty(pkColumnName)) {
        final StringBuilder sb = new StringBuilder();
        sb.append(tableName);
        sb.append('_');
        sb.append(pkColumnName);
        sb.append("_seq");

        params.setProperty(SEQUENCE_PARAM, sb.toString());
      }
    }
    super.configure(type, params, serviceRegistry);
  }
}
