package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;


/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name="master_quant_reporter_ion")
public class MasterQuantReporterIon implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="ms_query_id")
	private Integer msQueryId;

	@ManyToOne
	@JoinColumn(name = "master_quant_component_id")
	private MasterQuantComponent masterQuantComponent;

	@ManyToOne
	@JoinColumn(name = "master_quant_peptide_ion_id")
	private MasterQuantPeptideIon masterQuantPeptideIon;
	
	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	
    public MasterQuantReporterIon() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public ResultSummary getResultSummary() {
		return resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Integer getMsQueryId() {
		return msQueryId;
	}

	public void setMsQueryId(Integer msQueryId) {
		this.msQueryId = msQueryId;
	}

	public MasterQuantComponent getMasterQuantComponent() {
		return masterQuantComponent;
	}

	public void setMasterQuantComponent(MasterQuantComponent masterQuantComponent) {
		this.masterQuantComponent = masterQuantComponent;
	}

	public MasterQuantPeptideIon getMasterQuantPeptideIon() {
		return masterQuantPeptideIon;
	}

	public void setMasterQuantPeptideIon(MasterQuantPeptideIon masterQuantPeptideIon) {
		this.masterQuantPeptideIon = masterQuantPeptideIon;
	}

}