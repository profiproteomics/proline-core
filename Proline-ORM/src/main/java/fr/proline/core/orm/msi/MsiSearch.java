package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;


/**
 * The persistent class for the msi_search database table.
 * 
 */
@Entity
@Table(name="msi_search")
public class MsiSearch implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Timestamp date;

	@Column(name="queries_count")
	private Integer queriesCount;

	@Column(name="result_file_name")
	private String resultFileName;

	@Column(name="result_file_directory")
	private String resultFileDirectory;

	@Column(name="searched_sequences_count")
	private Integer searchedSequencesCount;

	@Column(name="job_number")
	private Integer jobNumber;
	
	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="submitted_queries_count")
	private Integer submittedQueriesCount;

	private String title;

	@Column(name="user_email")
	private String userEmail;

	@Column(name="user_name")
	private String userName;

	//uni-directional many-to-one association to Peaklist
    @ManyToOne
	private Peaklist peaklist;

	//uni-directional many-to-one association to SearchSetting
    @ManyToOne
	@JoinColumn(name="search_settings_id")
	private SearchSetting searchSetting;

    public MsiSearch() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Timestamp getDate() {
		return this.date;
	}

	public void setDate(Timestamp date) {
		this.date = date;
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

	public Integer getSubmittedQueriesCount() {
		return this.submittedQueriesCount;
	}

	public void setSubmittedQueriesCount(Integer submittedQueriesCount) {
		this.submittedQueriesCount = submittedQueriesCount;
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

}