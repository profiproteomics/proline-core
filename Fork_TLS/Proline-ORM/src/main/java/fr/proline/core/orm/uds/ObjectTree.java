package fr.proline.core.orm.uds;

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
@Entity(name = "fr.proline.core.orm.uds.ObjectTree")
@Table(name = "object_tree")
public class ObjectTree implements Serializable {

    private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "blob_data")
	private byte[] blobData;

	@Column(name = "clob_data")
	private String clobData;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@ManyToOne(cascade = CascadeType.PERSIST)
	@JoinColumn(name = "schema_name")
	private ObjectTreeSchema schema;

    public ObjectTree() {
    }
    
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

	public ObjectTreeSchema getSchema() {
		return schema;
	}

	public void setSchema(ObjectTreeSchema schema) {
		this.schema = schema;
	}

    
}
