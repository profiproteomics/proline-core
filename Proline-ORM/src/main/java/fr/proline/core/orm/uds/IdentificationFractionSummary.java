package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the identification_fraction_summary database table.
 * 
 */
@Entity
@Table(name="identification_fraction_summary")
public class IdentificationFractionSummary implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="result_summary_id")
	private Integer resultSummaryId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to Fraction
    @ManyToOne
	@JoinColumn(name="identification_fraction_id")
	private IdentificationFraction fraction;

	//bi-directional many-to-one association to IdentificationSummary
    @ManyToOne
	@JoinColumn(name="identification_summary_id")
	private IdentificationSummary identificationSummary;

    public IdentificationFractionSummary() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public IdentificationFraction getFraction() {
		return this.fraction;
	}

	public void setFraction(IdentificationFraction fraction) {
		this.fraction = fraction;
	}
	
	public IdentificationSummary getIdentificationSummary() {
		return this.identificationSummary;
	}

	public void setIdentificationSummary(IdentificationSummary identificationSummary) {
		this.identificationSummary = identificationSummary;
	}
	
}