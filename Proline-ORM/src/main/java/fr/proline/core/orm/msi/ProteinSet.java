package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import fr.profi.util.StringUtils;
import fr.proline.core.orm.util.JsonSerializer;

/**
 * The persistent class for the protein_set database table.
 * 
 */
@Entity
@Table(name = "protein_set")
public class ProteinSet implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "is_decoy")
	private boolean isDecoy;

	@Column(name = "is_validated")
	private boolean isValidated;

	@Column(name = "master_quant_component_id")
	private Long masterQuantComponentId;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@Column(name = "selection_level")
	private int selectionLevel;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// bi-directional one-to-one association to PeptideSet
	// @OneToOne(mappedBy="proteinSet",fetch = FetchType.LAZY)
	// private PeptideSet peptideOverSet;

	@Column(name = "representative_protein_match_id")
	private long representativeProteinMatchId;

	// bi-directional many-to-one association to ProteinSetProteinMatchItem
	@OneToMany(mappedBy = "proteinSet")
	private Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems;

	@ElementCollection
	@MapKeyColumn(name = "schema_name")
	@Column(name = "object_tree_id")
	@CollectionTable(name = "protein_set_object_tree_map", joinColumns = @JoinColumn(name = "protein_set_id", referencedColumnName = "id") )
	private Map<String, Long> objectTreeIdByName;

	@Transient
	private Map<String, Object> serializedPropertiesMap;

	public ProteinSet() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public boolean getIsDecoy() {
		return isDecoy;
	}

	public void setIsDecoy(final boolean pIsDecoy) {
		isDecoy = pIsDecoy;
	}

	public boolean getIsValidated() {
		return isValidated;
	}

	public void setIsValidated(final boolean pIsValidated) {
		isValidated = pIsValidated;
	}

	public Long getMasterQuantComponentId() {
		return masterQuantComponentId;
	}

	public void setMasterQuantComponentId(final Long pMasterQuantComponentId) {
		masterQuantComponentId = pMasterQuantComponentId;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public int getSelectionLevel() {
		return selectionLevel;
	}

	public void setSelectionLevel(final int pSelectionLevel) {
		selectionLevel = pSelectionLevel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
		this.serializedPropertiesMap = null;
	}

	/*
	 * public PeptideSet getPeptideOverSet() { return null; //this.peptideOverSet; }
	 * 
	 * public void setPeptideOverSet(PeptideSet peptideSet) { //this.peptideOverSet = peptideSet; }
	 */

	public long getProteinMatchId() {
		return representativeProteinMatchId;
	}

	public void setProteinMatchId(final long pProteinMatchId) {
		representativeProteinMatchId = pProteinMatchId;
	}

	public Set<ProteinSetProteinMatchItem> getProteinSetProteinMatchItems() {
		return this.proteinSetProteinMatchItems;
	}

	public void setProteinSetProteinMatchItems(Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems) {
		this.proteinSetProteinMatchItems = proteinSetProteinMatchItems;
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

	@SuppressWarnings("unchecked")
	public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
			serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(),
				Map.class);
		}
		return serializedPropertiesMap;
	}

	public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
		this.serializedPropertiesMap = serializedPropertiesMap;
		this.serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
	}

}
