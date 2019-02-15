package fr.proline.core.orm;

import java.io.Serializable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The persistent class for the object_tree database table.
 * 
 */
@Entity
@Table(name = "object_tree")
public abstract class AbstractObjectTree implements Serializable {

	protected static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	protected long id;

	@Column(name = "blob_data")
	protected byte[] blobData;

	@Column(name = "clob_data")
	protected String clobData;

	@Column(name = "serialized_properties")
	protected String serializedProperties;

	@ManyToOne(cascade = CascadeType.PERSIST)
	@JoinColumn(name = "schema_name")
	protected AbstractObjectTreeSchema schema;

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public byte[] getBlobData() {
		return this.blobData;
	}

	public void setBlobData(byte[] blobData) {
		this.blobData = blobData;
	}

	public String getClobData() {
		return clobData;
	}

	public void setClobData(String clobData) {
		this.clobData = clobData;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public AbstractObjectTreeSchema getSchema() {
		return schema;
	}

	public void setSchema(AbstractObjectTreeSchema schema) {
		this.schema = schema;
	}

}