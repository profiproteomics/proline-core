package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

/**
 * The persistent class for the document database table.
 * 
 */
@Entity
public class Document implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "creation_log")
	private String creationLog;

	@Column(name = "creation_timestamp")
	private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

	private String description;

	private String keywords;

	@Column(name = "modification_log")
	private String modificationLog;

	@Column(name = "modification_timestamp")
	private Timestamp modificationTimestamp;

	private String name;

	@Column(name = "object_tree_id")
	private long objectTreeId;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "schema_name")
	private String schemaName;

	// bi-directional many-to-one association to Project
	@ManyToOne
	@JoinColumn(name = "project_id")
	private Project project;

	// bi-directional many-to-one association to VirtualFolder
	@ManyToOne
	@JoinColumn(name = "virtual_folder_id")
	private VirtualFolder folder;

	public Document() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getCreationLog() {
		return this.creationLog;
	}

	public void setCreationLog(String creationLog) {
		this.creationLog = creationLog;
	}

	public Timestamp getCreationTimestamp() {
		Timestamp result = null;

		if (creationTimestamp != null) { // Should not be null
			result = (Timestamp) creationTimestamp.clone();
		}

		return result;
	}

	public void setCreationTimestamp(final Timestamp pCreationTimestamp) {

		if (pCreationTimestamp == null) {
			throw new IllegalArgumentException("PCreationTimestamp is null");
		}

		creationTimestamp = (Timestamp) pCreationTimestamp.clone();
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getKeywords() {
		return this.keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getModificationLog() {
		return this.modificationLog;
	}

	public void setModificationLog(String modificationLog) {
		this.modificationLog = modificationLog;
	}

	public Timestamp getModificationTimestamp() {
		Timestamp result = null;

		if (modificationTimestamp != null) {
			result = (Timestamp) modificationTimestamp.clone();
		}

		return result;
	}

	public void setModificationTimestamp(final Timestamp pModificationTimestamp) {

		if (pModificationTimestamp == null) {
			modificationTimestamp = null;
		} else {
			modificationTimestamp = (Timestamp) pModificationTimestamp.clone();
		}

	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getObjectTreeId() {
		return objectTreeId;
	}

	public void setObjectTreeId(final long pObjectTreeId) {
		objectTreeId = pObjectTreeId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getSchemaName() {
		return this.schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public Project getProject() {
		return this.project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public VirtualFolder getFolder() {
		return this.folder;
	}

	public void setFolder(VirtualFolder folder) {
		this.folder = folder;
	}

}
