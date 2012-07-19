package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;


/**
 * The persistent class for the admin_infos database table.
 * 
 */
@Entity(name="fr.proline.core.orm.pdi.ObjectTree")
@Table(name="object_tree")
public class ObjectTree implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="serialized_data")
	private String serializedData;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@ManyToOne
	@JoinColumn(name="schema_name")
	private ObjectTreeSchema schemaName;
	
	public ObjectTree() {
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getSerializedData() {
		return serializedData;
	}

	public void setSerializedData(String serializedData) {
		this.serializedData = serializedData;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public ObjectTreeSchema getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(ObjectTreeSchema schemaName) {
		this.schemaName = schemaName;
	}

}