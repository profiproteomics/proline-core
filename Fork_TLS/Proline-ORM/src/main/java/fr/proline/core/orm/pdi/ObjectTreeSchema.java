package fr.proline.core.orm.pdi;

import javax.persistence.Entity;

import fr.proline.core.orm.AbstractObjectTreeSchema;

/**
 * The persistent class for the object_tree_schema database table.
 * 
 */
@Entity(name="fr.proline.core.orm.pdi.ObjectTreeSchema")
public class ObjectTreeSchema extends AbstractObjectTreeSchema {
	  
	private static final long serialVersionUID = 1L;
	
	public ObjectTreeSchema() {}
	
}