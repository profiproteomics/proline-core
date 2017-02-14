package fr.proline.core.orm.pdi;

import javax.persistence.Entity;

import fr.proline.core.orm.AbstractObjectTree;

/**
 * The persistent class for the object_tree database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.pdi.ObjectTree")
public class ObjectTree extends AbstractObjectTree {

    private static final long serialVersionUID = 1L;

    public ObjectTree() {}

}
