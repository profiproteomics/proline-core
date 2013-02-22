package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Date;


/**
 * The persistent class for the document database table.
 * 
 */
@Entity
public class Document implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="creation_log")
	private String creationLog;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

	private String description;

	private String keywords;

	@Column(name="modification_log")
	private String modificationLog;

	@Column(name="modification_timestamp")
	private Timestamp modificationTimestamp;

	private String name;

	@Column(name="object_tree_id")
	private Integer objectTreeId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="schema_name")
	private String schemaName;

	//bi-directional many-to-one association to Project
    @ManyToOne
	private Project project;

	//bi-directional many-to-one association to VirtualFolder
    @ManyToOne
	@JoinColumn(name="virtual_folder_id")
	private VirtualFolder folder;

    public Document() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getCreationLog() {
		return this.creationLog;
	}

	public void setCreationLog(String creationLog) {
		this.creationLog = creationLog;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
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
		return this.modificationTimestamp;
	}

	public void setModificationTimestamp(Timestamp modificationTimestamp) {
		this.modificationTimestamp = modificationTimestamp;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getObjectTreeId() {
		return this.objectTreeId;
	}

	public void setObjectTreeId(Integer objectTreeId) {
		this.objectTreeId = objectTreeId;
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