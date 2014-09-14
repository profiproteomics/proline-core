package fr.proline.core.orm;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the object_tree_schema database table.
 * 
 */
@Entity
@Table(name="object_tree_schema")
public abstract class AbstractObjectTreeSchema implements Serializable {
	
	protected static final long serialVersionUID = 1L;

	@Id
	@Column(name = "name")
	protected String name;

	@Column(name = "type")
	protected String type;

	@Column(name = "is_binary_mode")
	protected boolean isBinaryMode;

	@Column(name = "version")
	protected String version;

	@Column(name = "schema")
	protected String schema;

	@Column(name = "description")
	protected String description;

	@Column(name = "serialized_properties")
	protected String serializedProperties;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean getIsBinaryMode() {
		return this.isBinaryMode;
	}

	public void setIsBinaryMode(boolean isBinaryMode) {
		this.isBinaryMode = isBinaryMode;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

}
