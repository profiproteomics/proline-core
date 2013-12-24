package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

/**
 * The persistent class for the project database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findProjectsByMembership", query = "Select p from Project p, UserAccount u where u.id=:id and u member OF p.members"),
	@NamedQuery(name = "findProjectsByOwner", query = "Select p from Project p where p.owner.id=:id"),
	@NamedQuery(name = "findAllProjectIds", query = "select p.id from fr.proline.core.orm.uds.Project p order by p.id") })
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_timestamp")
    private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

    private String description;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to Document
    @OneToMany(mappedBy = "project")
    private Set<Document> documents;

    // bi-directional many-to-one association to UserAccount
    @ManyToOne
    private UserAccount owner;

    // bi-directional many-to-many association to ExternalDb
    @ManyToMany
    @JoinTable(name = "project_db_map", joinColumns = { @JoinColumn(name = "project_id") }, inverseJoinColumns = { @JoinColumn(name = "external_db_id") })
    private Set<ExternalDb> externalDatabases;

    // bi-directional many-to-one association to VirtualFolder
    @OneToMany(mappedBy = "project")
    private Set<VirtualFolder> folders;

    // bi-directional many-to-many association to UserAccount
    @ManyToMany
    @JoinColumn(name = "id")
    @JoinTable(name = "project_user_account_map", inverseJoinColumns = @JoinColumn(name = "user_account_id", referencedColumnName = "id"), joinColumns = @JoinColumn(name = "project_id", referencedColumnName = "id"))
    private Set<UserAccount> members;
    
    // bi-directional many-to-many association to RawFile
    @ManyToMany
    @JoinTable(name = "raw_file_project_map", joinColumns = { @JoinColumn(name = "project_id") }, inverseJoinColumns = { @JoinColumn(name = "raw_file_name") })
    private Set<RawFile> rawFiles;

    // Transient Variables not saved in database
    @Transient
    private TransientData transientData = null;

    protected Project() {
    }

    public Project(UserAccount owner) {
	setOwner(owner);
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
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

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
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

    public UserAccount getOwner() {
	return this.owner;
    }

    public void setOwner(UserAccount owner) {
	this.owner = owner;
	addMember(owner);
    }

    public void setExternalDatabases(final Set<ExternalDb> externalDatabases) {
	this.externalDatabases = externalDatabases;
    }

    public Set<ExternalDb> getExternalDatabases() {
	return this.externalDatabases;
    }

    public void addExternalDatabase(final ExternalDb externalDb) {

	if (externalDb != null) {
	    Set<ExternalDb> externalDbs = getExternalDatabases();

	    if (externalDbs == null) {
		externalDbs = new HashSet<ExternalDb>();

		setExternalDatabases(externalDbs);
	    }

	    externalDbs.add(externalDb);
	}

    }

    public void removeExternalDb(final ExternalDb externalDb) {
	final Set<ExternalDb> externalDbs = getExternalDatabases();

	if (externalDbs != null) {
	    externalDbs.remove(externalDb);
	}

    }

    public Set<VirtualFolder> getFolders() {
	return this.folders;
    }

    public void setFolders(Set<VirtualFolder> folders) {
	this.folders = folders;
    }

    void setMembers(final Set<UserAccount> pMembers) {
	members = pMembers;
    }

    public Set<UserAccount> getMembers() {
	return members;
    }

    public void addMember(final UserAccount member) {

	if (member != null) {
	    Set<UserAccount> localMembers = getMembers();

	    if (localMembers == null) {
		localMembers = new HashSet<UserAccount>();

		setMembers(localMembers);
	    }

	    localMembers.add(member);
	}

    }

    public void removeMember(final UserAccount member) {
	final Set<UserAccount> localMembers = getMembers();

	if (localMembers != null) {
	    localMembers.remove(member);
	}

    }
    
    public void setRawFiles(final Set<RawFile> rawFiles) {
	this.rawFiles = rawFiles;
    }

    public Set<RawFile> getRawFiles() {
	return this.rawFiles;
    }

    public void addRawFile(final RawFile rawFile) {

	if (rawFile != null) {
	    Set<RawFile> rawFiles = getRawFiles();

	    if (rawFiles == null) {
	    	rawFiles = new HashSet<RawFile>();

	    	setRawFiles(rawFiles);
	    }

	    rawFiles.add(rawFile);
	}

    }

    public void removeRawFile(final RawFile rawFile) {
	final Set<RawFile> rawFiles = getRawFiles();

	if (rawFiles != null) {
		rawFiles.remove(rawFile);
	}

    }

    public TransientData getTransientData() {
	if (transientData == null) {
	    transientData = new TransientData();
	}
	return transientData;
    }

    /**
     * Transient Data which will be not saved in database Used by the Proline Studio IHM
     * 
     * @author JM235353
     */
    public static class TransientData implements Serializable {

	private static final long serialVersionUID = 1L;
	private int childrenNumber = 0;

	protected TransientData() {
	}

	public int getChildrenNumber() {
	    return childrenNumber;
	}

	public void setChildrenNumber(int childrenNumber) {
	    this.childrenNumber = childrenNumber;
	}

    }

}
