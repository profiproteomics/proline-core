package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the msi_search database table.
 * 
 */
@Entity
@Table(name = "msi_search")
public class MsiSearch implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private Timestamp date;

    @Column(name = "queries_count")
    private Integer queriesCount;

    @Column(name = "result_file_name")
    private String resultFileName;

    @Column(name = "result_file_directory")
    private String resultFileDirectory;

    @Column(name = "searched_sequences_count")
    private Integer searchedSequencesCount;

    @Column(name = "job_number")
    private Integer jobNumber;

    @Column(name = "serialized_properties")
    private String serializedProperties;


    private String title;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_name")
    private String userName;

    // uni-directional many-to-one association to Peaklist
    @ManyToOne
    private Peaklist peaklist;

    // uni-directional many-to-one association to SearchSetting
    @ManyToOne
    @JoinColumn(name = "search_settings_id")
    private SearchSetting searchSetting;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "msi_search_object_tree_map", joinColumns = @JoinColumn(name = "msi_search_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    public MsiSearch() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public Timestamp getDate() {
	Timestamp result = null;

	if (date != null) {
	    result = (Timestamp) date.clone();
	}

	return result;
    }

    public void setDate(final Timestamp pDate) {

	if (pDate == null) {
	    date = null;
	} else {
	    date = (Timestamp) pDate.clone();
	}

    }

    public Integer getQueriesCount() {
	return this.queriesCount;
    }

    public void setQueriesCount(Integer queriesCount) {
	this.queriesCount = queriesCount;
    }

    public String getResultFileName() {
	return resultFileName;
    }

    public void setResultFileName(String resultFileName) {
	this.resultFileName = resultFileName;
    }

    public String getResultFileDirectory() {
	return resultFileDirectory;
    }

    public void setResultFileDirectory(String resultFileDirectory) {
	this.resultFileDirectory = resultFileDirectory;
    }

    public Integer getJobNumber() {
	return jobNumber;
    }

    public void setJobNumber(Integer jobNumber) {
	this.jobNumber = jobNumber;
    }

    public Integer getSearchedSequencesCount() {
	return this.searchedSequencesCount;
    }

    public void setSearchedSequencesCount(Integer searchedSequencesCount) {
	this.searchedSequencesCount = searchedSequencesCount;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getTitle() {
	return this.title;
    }

    public void setTitle(String title) {
	this.title = title;
    }

    public String getUserEmail() {
	return this.userEmail;
    }

    public void setUserEmail(String userEmail) {
	this.userEmail = userEmail;
    }

    public String getUserName() {
	return this.userName;
    }

    public void setUserName(String userName) {
	this.userName = userName;
    }

    public Peaklist getPeaklist() {
	return this.peaklist;
    }

    public void setPeaklist(Peaklist peaklist) {
	this.peaklist = peaklist;
    }

    public SearchSetting getSearchSetting() {
	return this.searchSetting;
    }

    public void setSearchSetting(SearchSetting searchSetting) {
	this.searchSetting = searchSetting;
    }

    void setObjectTreeIdByName(final Map<String, Long> objectTree) {
	objectTreeIdByName = objectTree;
    }

    public Map<String, Long> getObjectTreeIdByName() {
	return objectTreeIdByName;
    }

    public Long putObject(final String schemaName, final long objectId) {

	if (StringUtils.isEmpty(schemaName)) {
	    throw new IllegalArgumentException("Invalid schemaName");
	}

	Map<String, Long> localObjectTree = getObjectTreeIdByName();

	if (localObjectTree == null) {
	    localObjectTree = new HashMap<String, Long>();

	    setObjectTreeIdByName(localObjectTree);
	}

	return localObjectTree.put(schemaName, Long.valueOf(objectId));
    }

    public Long removeObject(final String schemaName) {
	Long result = null;

	final Map<String, Long> localObjectTree = getObjectTreeIdByName();
	if (localObjectTree != null) {
	    result = localObjectTree.remove(schemaName);
	}

	return result;
    }

    @Override
    public String toString() {
	return new ToStringBuilder(this).append("id", getId()).append("title", getTitle())
		.append("job", getJobNumber()).append("result filename", getResultFileName()).toString();
    }

}
