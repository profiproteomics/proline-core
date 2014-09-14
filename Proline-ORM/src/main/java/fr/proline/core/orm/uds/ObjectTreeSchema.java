package fr.proline.core.orm.uds;

import javax.persistence.Entity;

import fr.proline.core.orm.AbstractObjectTreeSchema;

/**
 * The persistent class for the object_tree_schema database table.
 * 
 */
@Entity(name="fr.proline.core.orm.uds.ObjectTreeSchema")
public class ObjectTreeSchema extends AbstractObjectTreeSchema {
	  
	private static final long serialVersionUID = 1L;
	
    public enum SchemaName {    	
    	LABEL_FREE_QUANT_CONFIG("quantitation.label_free_config"),
    	SPECTRAL_COUNTING_QUANT_CONFIG("quantitation.spectral_counting_config");
    	
    	private final String name;
    	
	    private SchemaName(final String name) {
	        this.name = name;
	    }
	    
	    @Override
	    public String toString() {
	        return name;
	    }
    };
	
	public ObjectTreeSchema() {}
	
}