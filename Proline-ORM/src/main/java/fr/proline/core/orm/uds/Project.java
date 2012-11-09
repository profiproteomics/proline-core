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


/**
 * The persistent class for the project database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(
		  name="findProjectsByMembership",
		  query="Select p from Project p, UserAccount u where u.id=:id and u member OF p.members"),
	@NamedQuery(
		  name="findProjectsByOwner",
		  query="Select p from Project p where p.owner.id=:id")
})
public class Project implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

	private String description;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Document
	@OneToMany(mappedBy="project")
	private Set<Document> documents;

	//bi-directional many-to-one association to UserAccount
    @ManyToOne
	private UserAccount owner;

	//bi-directional many-to-many association to ExternalDb
    @ManyToMany
	@JoinTable(
		name="project_db_map"
		, joinColumns={
			@JoinColumn(name="project_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="external_db_id")
			}
		)
	private Set<ExternalDb> externalDatabases;

	//bi-directional many-to-one association to VirtualFolder
	@OneToMany(mappedBy="project")
	private Set<VirtualFolder> folders;

	//bi-directional many-to-many association to UserAccount
    @ManyToMany
	@JoinColumn(name="id")
	@JoinTable(name = "project_user_account_map", inverseJoinColumns = @JoinColumn(name = "user_account_id", referencedColumnName = "id"), joinColumns = @JoinColumn(name = "project_id", referencedColumnName = "id"))
	private Set<UserAccount> members;

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
	
	public Set<ExternalDb> getExternalDatabases() {
		return this.externalDatabases;
	}

	public void setExternalDatabases(Set<ExternalDb> externalDatabases) {
		this.externalDatabases = externalDatabases;
	}
	
    public boolean addExternalDatabase(ExternalDb extDb) {
      if(this.externalDatabases == null)
          this.externalDatabases = new HashSet<ExternalDb>();
      return this.externalDatabases.add(extDb);
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

	public boolean addMember(UserAccount member) {
		if(this.members == null)
			this.members = new HashSet<UserAccount>();
		return this.members.add(member);
	}
	
	public boolean removeMember(UserAccount member) {
		return ((this.members != null) && (this.members.remove(member)));
	}
	
	protected void setMembers(Set<UserAccount> members) {
		this.members = members;
	}
	
}