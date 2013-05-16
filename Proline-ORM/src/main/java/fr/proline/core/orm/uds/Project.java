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
    private Integer id;

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

    // Transient Variables not saved in database
    @Transient
    private TransientData transientData = null;

    protected Project() {
    }

    public Project(UserAccount owner) {
	this.owner = owner;
	setOwner(owner);
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
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

    public Set<UserAccount> getMembers() {
	return this.members;
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

    protected void setMembers(Set<UserAccount> members) {
	this.members = members;
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