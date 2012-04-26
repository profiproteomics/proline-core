package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Date;
import java.util.Set;


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

    @Temporal( TemporalType.DATE)
	private Date date;

	@Column(name="queries_count")
	private Integer queriesCount;

	@Column(name="result_file_number")
	private Integer resultFileNumber;

	@Column(name="result_file_path")
	private String resultFilePath;

	@Column(name="searched_sequences_count")
	private Integer searchedSequencesCount;

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

	public Date getDate() {
		return this.date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Integer getQueriesCount() {
		return this.queriesCount;
	}

	public void setQueriesCount(Integer queriesCount) {
		this.queriesCount = queriesCount;
	}

	public Integer getResultFileNumber() {
		return this.resultFileNumber;
	}

	public void setResultFileNumber(Integer resultFileNumber) {
		this.resultFileNumber = resultFileNumber;
	}

	public String getResultFilePath() {
		return this.resultFilePath;
	}

	public void setResultFilePath(String resultFilePath) {
		this.resultFilePath = resultFilePath;
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