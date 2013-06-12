package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the virtual_folder database table.
 * 
 */
@Entity
@Table(name = "virtual_folder")
public class VirtualFolder implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String name;

    private String path;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to Document
    @OneToMany(mappedBy = "folder")
    private Set<Document> documents;

    // bi-directional many-to-one association to Project
    @ManyToOne
    private Project project;

    // bi-directional many-to-one association to VirtualFolder
    @ManyToOne
    @JoinColumn(name = "parent_virtual_folder_id")
    private VirtualFolder parentFolder;

    // bi-directional many-to-one association to VirtualFolder
    @OneToMany(mappedBy = "parentFolder")
    private Set<VirtualFolder> subFolders;

    public VirtualFolder() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getPath() {
	return this.path;
    }

    public void setPath(String path) {
	this.path = path;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public Set<Document> getDocuments() {
	return this.documents;
    }

    public void setDocuments(Set<Document> documents) {
	this.documents = documents;
    }

    public Project getProject() {
	return this.project;
    }

    public void setProject(Project project) {
	this.project = project;
    }

    public VirtualFolder getParentFolder() {
	return this.parentFolder;
    }

    public void setParentFolder(VirtualFolder parentFolder) {
	this.parentFolder = parentFolder;
    }

    public Set<VirtualFolder> getSubFolders() {
	return this.subFolders;
    }

    public void setSubFolders(Set<VirtualFolder> subFolders) {
	this.subFolders = subFolders;
    }

}
