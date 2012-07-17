package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.*;

/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name = "master_quant_peptide_ion")
public class MasterQuantPeptideIon implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;

	@Column(name = "charge")
	private Integer charge;

	@Column(name = "moz")
	private Double moz;

	@Column(name = "elution_time")
	private Float elutionTime;

	@Column(name = "scan_number")
	private Integer scanNumber;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "lcms_feature_id")
	private Integer lcmsFeatureId;

	@Column(name = "peptide_id")
	private Integer peptideId;

	@Column(name = "peptide_instance_id")
	private Integer peptideInstanceId;

	@Column(name = "best_peptide_match_id")
	private Integer bestPeptideMatchId;

	@Column(name = "unmodified_peptide_ion_id")
	private Integer unmodifiedPeptideIonId;

	@ManyToOne
	@JoinColumn(name = "master_quant_component_id")
	private MasterQuantComponent masterQuantComponent;

	@OneToMany(mappedBy="masterQuantPeptideIon")
	private Set<MasterQuantReporterIon> masterQuantReporterIons;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;


	public MasterQuantPeptideIon() {
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getCharge() {
		return charge;
	}

	public void setCharge(Integer charge) {
		this.charge = charge;
	}

	public Double getMoz() {
		return moz;
	}

	public void setMoz(Double moz) {
		this.moz = moz;
	}

	public Float getElutionTime() {
		return elutionTime;
	}

	public void setElutionTime(Float elutionTime) {
		this.elutionTime = elutionTime;
	}

	public Integer getScanNumber() {
		return scanNumber;
	}

	public void setScanNumber(Integer scanNumber) {
		this.scanNumber = scanNumber;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Integer getLcmsFeatureId() {
		return lcmsFeatureId;
	}

	public void setLcmsFeatureId(Integer lcmsFeatureId) {
		this.lcmsFeatureId = lcmsFeatureId;
	}

	public Integer getPeptideId() {
		return peptideId;
	}

	public void setPeptideId(Integer peptideId) {
		this.peptideId = peptideId;
	}

	public Integer getPeptideInstanceId() {
		return peptideInstanceId;
	}

	public void setPeptideInstanceId(Integer peptideInstanceId) {
		this.peptideInstanceId = peptideInstanceId;
	}

	public Integer getBestPeptideMatchId() {
		return bestPeptideMatchId;
	}

	public void setBestPeptideMatchId(Integer bestPeptideMatchId) {
		this.bestPeptideMatchId = bestPeptideMatchId;
	}
	
  public Integer getUnmodifiedPeptideIonId() {
    return unmodifiedPeptideIonId;
  }

  public void setUnmodifiedPeptideIonId(Integer unmodifiedPeptideIonId) {
    this.unmodifiedPeptideIonId = unmodifiedPeptideIonId;
  }

	public MasterQuantComponent getMasterQuantComponent() {
		return masterQuantComponent;
	}

	public void setMasterQuantComponent(MasterQuantComponent masterQuantComponent) {
		this.masterQuantComponent = masterQuantComponent;
	}

	public ResultSummary getResultSummary() {
		return resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public Set<MasterQuantReporterIon> getMasterQuantReporterIons() {
		return masterQuantReporterIons;
	}

	public void setMasterQuantReporterIons(Set<MasterQuantReporterIon> masterQuantReporterIons) {
		this.masterQuantReporterIons = masterQuantReporterIons;
	}

}