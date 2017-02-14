package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.ObjectTreeSchema;
import fr.proline.repository.util.JPAUtils;

public final class ObjectTreeSchemaRepository {
	
	private static Logger logger = LoggerFactory.getLogger(ObjectTreeSchemaRepository.class);
    private ObjectTreeSchemaRepository() {

    }

    
    public static ObjectTreeSchema loadOrCreateObjectTreeSchema (final EntityManager udsEm, final String schemaName) {

    	JPAUtils.checkEntityManager(udsEm);	
    	ObjectTreeSchema objTreeSchema = udsEm.find(ObjectTreeSchema.class, schemaName);
			    
	    // Create a faked schema if the requested one has not been found
	    if(objTreeSchema == null) {
	      	logger.warn("Schema "+schemaName+" has not been find in the UDSdb");
			      
		    objTreeSchema = new ObjectTreeSchema();
		    objTreeSchema.setName(schemaName);
		    objTreeSchema.setType("JSON");
		    objTreeSchema.setVersion("0.1");
		    objTreeSchema.setSchema("");
	    }
			    
	    	return objTreeSchema;
    }
	
}
