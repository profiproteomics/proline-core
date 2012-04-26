package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the identification_summary database table.
 * 
 */
@Entity
@Table(name="identification_summary")
public class IdentificationSummary implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer number;

	@Column(name="result_summary_id")
	private Integer resultSummaryId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to FractionSummary
	@OneToMany(mappedBy="identificationSummary")
	private Set<IdentificationFractionSummary> fractionSummaries;

	//bi-directional many-to-one association to Identification
    @ManyToOne
	private Identification identification;

    public IdentificationSummary() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public Integer getResultSummaryId() {
		return this.resultSummaryId;
	}

	public void setResultSummaryId(Integer resultSummaryId) {
		this.resultSummaryId = resultSummaryId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Set<IdentificationFractionSummary> getFractionSummaries() {
		return this.fractionSummaries;
	}

	public void setFractionSummaries(Set<IdentificationFractionSummary> fractionSummaries) {
		this.fractionSummaries = fractionSummaries;
	}
	
	public Identification getIdentification() {
		return this.identification;
	}

	public void setIdentification(Identification identification) {
		this.identification = identification;
	}
	
}