package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;


/**
 * The persistent class for the identification database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name="findIdentificationsByProject",
				query="Select i from Identification i where i.project.id =:id"),
    @NamedQuery(name="findIdentificationNamesByProject",
		   query="Select i.name from Identification i where i.project.id =:id"),
    @NamedQuery(name="findIdentificationByName",
	   query="Select i from Identification i where i.project.id =:id and i.name=:name")
})
public class Identification implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp;

	private String description;

	@Column(name="fraction_count")
	private Integer fractionCount;

	@Column(name="fractionation_type")
	private String fractionationType;

	private String keywords;

	@Column(name="modification_log")
	private String modificationLog;

	private String name;

	private Integer number;

	@ManyToOne
	@JoinColumn(name="project_id")
	private Project project;


	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Fraction
	@OneToMany(mappedBy="identification")
	@OrderBy("number")
	private List<IdentificationFraction> fractions;

	//bi-directional many-to-one association to IdentificationSummary
	@OneToMany(mappedBy="identification")
	private Set<IdentificationSummary> identificationSummaries;

	@OneToOne
	@JoinColumn(name="active_summary_id")
	private IdentificationSummary activeSummary;
	

	protected Identification() {
    }

	public Identification(Project project) {
		this.project = project;
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

	public Integer getFractionCount() {
		return this.fractionCount;
	}

	public void setFractionCount(Integer fractionCount) {
		this.fractionCount = fractionCount;
	}

	public String getFractionationType() {
		return this.fractionationType;
	}

	public void setFractionationType(String fractionationType) {
		this.fractionationType = fractionationType;
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

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public Project getProject() {
		return this.project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<IdentificationFraction> getFractions() {
		return this.fractions;
	}

	public void setFractions(List<IdentificationFraction> fractions) {
		this.fractions = fractions;
	}
	
	public Set<IdentificationSummary> getIdentificationSummaries() {
		return this.identificationSummaries;
	}

	public void setIdentificationSummaries(Set<IdentificationSummary> identificationSummaries) {
		this.identificationSummaries = identificationSummaries;
	}
	
    public IdentificationSummary getActiveSummary() {
		return activeSummary;
	}

	public void setActiveSummary(IdentificationSummary activeSummary) {
		this.activeSummary = activeSummary;
	}

	
}