package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.*;


/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name="master_quant_component")
public class MasterQuantComponent implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="selection_level")
	private Integer selectionLevel;

	@Column(name="schema_name")
	private String schemaName;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="object_tree_id")
	private Integer objectTreeId;
	
	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@OneToMany(mappedBy="masterQuantComponent")
	private Set<MasterQuantPeptideIon> masterQuantPeptideIons;

	@OneToMany(mappedBy="masterQuantComponent")
	private Set<MasterQuantReporterIon> masterQuantReporterIons;

    public MasterQuantComponent() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getSelectionLevel() {
		return selectionLevel;
	}

	public void setSelectionLevel(Integer selectionLevel) {
		this.selectionLevel = selectionLevel;
	}

	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Integer getObjectTreeId() {
		return objectTreeId;
	}

	public void setObjectTreeId(Integer objectTreeId) {
		this.objectTreeId = objectTreeId;
	}

	public ResultSummary getResultSummary() {
		return resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public Set<MasterQuantPeptideIon> getMasterQuantPeptideIons() {
		return masterQuantPeptideIons;
	}

	public void setMasterQuantPeptideIons(Set<MasterQuantPeptideIon> masterQuantPeptideIons) {
		this.masterQuantPeptideIons = masterQuantPeptideIons;
	}

	public Set<MasterQuantReporterIon> getMasterQuantReporterIons() {
		return masterQuantReporterIons;
	}

	public void setMasterQuantReporterIons(Set<MasterQuantReporterIon> masterQuantReporterIons) {
		this.masterQuantReporterIons = masterQuantReporterIons;
	}

}