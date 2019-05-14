package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the admin_infos database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.ObjectTreeSchema")
@Table(name = "object_tree_schema")
public class ObjectTreeSchema implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum SchemaName {
		SPECTRUM_MATCH("peptide_match.spectrum_match"),
		ISOBARIC_TAGGING_QUANT_PEPTIDES("object_tree.isobaric_tagging_quant_peptides"),
		ISOBARIC_TAGGING_QUANT_PEPTIDE_IONS("object_tree.isobaric_tagging_quant_peptide_ions"),
		LABEL_FREE_QUANT_PEPTIDES("object_tree.label_free_quant_peptides"),
		LABEL_FREE_QUANT_PEPTIDE_IONS("object_tree.label_free_quant_peptide_ions"),
		QUANT_PROTEIN_SETS("object_tree.quant_protein_sets"),
		QUANT_REPORTER_IONS("object_tree.quant_reporter_ions"),
		SPECTRAL_COUNTING_QUANT_PEPTIDES("object_tree.spectral_counting_quant_peptides"),
		SPECTRAL_COUNTING_QUANT_PEPTIDE_IONS("object_tree.spectral_counting_quant_peptide_ions"),
		PEPTIDE_VALIDATION_ROC_CURVE("result_summary.peptide_validation_roc_curve"),
		PROTEIN_VALIDATION_ROC_CURVE("result_summary.protein_validation_roc_curve"),
		PTM_SITES("result_summary.ptm_sites"),
		PTM_DATASET("result_summary.ptm_dataset"),
		ION_SERIES("peptide_match.ion_series"),
		RESIDUE_LABELING_QUANT_PEPTIDES("object_tree.residue_labeling_quant_peptides"),
		RESIDUE_LABELING_QUANT_PEPTIDE_IONS("object_tree.residue_labeling_quant_peptide_ions");

		private final String name;

		private SchemaName(final String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	};

	@Id
	@Column(name = "name")
	private String name;

	@Column(name = "type")
	private String type;

	@Column(name = "is_binary_mode")
	private boolean isBinaryMode;

	@Column(name = "version")
	private String version;

	@Column(name = "schema")
	private String schema;

	@Column(name = "description")
	private String description;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	public ObjectTreeSchema() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean getIsBinaryMode() {
		return this.isBinaryMode;
	}

	public void setIsBinaryMode(boolean isBinaryMode) {
		this.isBinaryMode = isBinaryMode;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

}