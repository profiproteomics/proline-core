package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
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
	@NamedQuery(name = "findProjectsByMembership", query = "Select p from Project p, ProjectUserAccountMap p2u, UserAccount u where u.id=:id and p2u.userAccount=u and p2u member OF p.projectUserAccountMap"),
	@NamedQuery(name = "findProjectsByOwner", query = "Select p from Project p where p.owner.id=:id"),
	@NamedQuery(name = "findAllProjectIds", query = "select p.id from fr.proline.core.orm.uds.Project p order by p.id") ,
	@NamedQuery(name = "findAllActiveProjectIds", query = "select p.id from fr.proline.core.orm.uds.Project p WHERE ( (p.serializedProperties is null) OR (p.serializedProperties is not null and p.serializedProperties NOT LIKE '%\"is_active\":false%'))") })

public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    
    private String name;

    private String description;

    @Column(name = "creation_timestamp")
    private Timestamp creationTimestamp = new Timestamp(new Date().getTime());
    
    @Column(name = "lock_expiration_timestamp")
    private Timestamp lockExpirationTStamp;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "lock_user_id")
    private Long lockUserID;
    
    // bi-directional many-to-one association to UserAccount
    @ManyToOne
    private UserAccount owner;
    
    // bi-directional many-to-one association to Document
    @OneToMany(mappedBy = "project")
    private Set<Document> documents;

    // bi-directional many-to-many association to ExternalDb
    @ManyToMany
    @JoinTable(name = "project_db_map", joinColumns = { @JoinColumn(name = "project_id") }, inverseJoinColumns = { @JoinColumn(name = "external_db_id") })
    private Set<ExternalDb> externalDatabases;

    // bi-directional many-to-one association to VirtualFolder
    @OneToMany(mappedBy = "project")
    private Set<VirtualFolder> folders;

    // bi-directional many-to-one association to ProjectUserAccountMap
    @OneToMany(mappedBy = "project", cascade=CascadeType.ALL)
    private Set<ProjectUserAccountMap> projectUserAccountMap;    

    // bi-directional many-to-many association to RawFile
    @ManyToMany
    @JoinTable(name = "raw_file_project_map", joinColumns = { @JoinColumn(name = "project_id") }, inverseJoinColumns = { @JoinColumn(name = "raw_file_identifier") })
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
    
    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }
    
    public String getDescription() {
	return this.description;
    }

    public void setDescription(String description) {
	this.description = description;
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

    public Timestamp getLockExpirationTimestamp(){
    	return this.lockExpirationTStamp;
    }
    
    public void setLockExpirationTimestamp(Timestamp lockTimestamp){
	this.lockExpirationTStamp = lockTimestamp;
    }
    
    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }
    
    public Long getLockUserID(){
    	return lockUserID;
    }
    
    public void setLockUserID(Long userID){
    	this.lockUserID = userID;
    }
    
    public UserAccount getOwner() {
	return this.owner;
    }

    public void setOwner(UserAccount owner) {
	this.owner = owner;
	addMember(owner, true);
    }

    public Set<Document> getDocuments() {
	return this.documents;
    }

    public void setDocuments(Set<Document> documents) {
	this.documents = documents;
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

    void setProjectUserAccountMap(final Set<ProjectUserAccountMap> userAccountMap) {
	this.projectUserAccountMap = userAccountMap;
    }

    public Set<ProjectUserAccountMap> getProjectUserAccountMap() {
	return projectUserAccountMap;
    }

    public void addMember(final UserAccount member, boolean writePermission) {

	if (member != null) {
		
	    Set<ProjectUserAccountMap> localMembers = getProjectUserAccountMap();
	     
	    ProjectUserAccountMapPK mapKey = new ProjectUserAccountMapPK();
	    mapKey.setProjectId(id);
	    mapKey.setUserAccountId(member.getId());
	    ProjectUserAccountMap newMember = new ProjectUserAccountMap();
	    newMember.setId(mapKey);
	    newMember.setProject(this);
	    newMember.setUserAccount(member);
	    newMember.setWritePermission(writePermission);
	    if (localMembers == null) {
		localMembers = new HashSet<ProjectUserAccountMap>();

		setProjectUserAccountMap(localMembers);
	    }

	    localMembers.add(newMember);
	}

    }

    public void removeMember(final UserAccount member) {
	final Set<ProjectUserAccountMap> localMembers = getProjectUserAccountMap();

	ProjectUserAccountMap userMap2Rem = null;
	if (localMembers != null) {
		for(ProjectUserAccountMap nextMember: localMembers){
			if(nextMember.getUserAccount().equals(member)){
				userMap2Rem = nextMember;
				break;
			}
				
		}
		if(userMap2Rem != null)
			localMembers.remove(userMap2Rem);
	}

    }
    
	public boolean isProjectMember(UserAccount member) {
		Set<ProjectUserAccountMap> localMembers = getProjectUserAccountMap();
		if (localMembers != null) {
			for (ProjectUserAccountMap element : localMembers) {
				if (element.getUserAccount().getId() == member.getId()) {
					return true;
				}

			}
		}

		return false;
	}

	// Adjective before noun is preferred => isProjectMember
	@Deprecated
	public boolean isMemberProject(UserAccount member) {
		return isProjectMember(member);
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
