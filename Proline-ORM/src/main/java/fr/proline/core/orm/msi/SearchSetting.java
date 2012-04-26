package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;
import java.util.Set;


/**
 * The persistent class for the search_settings database table.
 * 
 */
@Entity
@Inheritance(strategy=InheritanceType.JOINED)
@DiscriminatorColumn(discriminatorType=DiscriminatorType.INTEGER, name="id")
@Table(name="search_settings")
public class SearchSetting implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="is_decoy")
	private Boolean isDecoy;

	@Column(name="max_missed_cleavages")
	private Integer maxMissedCleavages;

	@Column(name="peptide_charge_states")
	private String peptideChargeStates;

	@Column(name="peptide_mass_error_tolerance")
	private double peptideMassErrorTolerance;

	@Column(name="peptide_mass_error_tolerance_unit")
	private String peptideMassErrorToleranceUnit;

	private String quantitation;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@Column(name="software_name")
	private String softwareName;

	@Column(name="software_version")
	private String softwareVersion;

	private String taxonomy;

	//uni-directional many-to-one association to InstrumentConfig
   @ManyToOne
	@JoinColumn(name="instrument_config_id")
	private InstrumentConfig instrumentConfig;

   @Column(name="instrument_config_id", insertable = false, updatable = false)
   private Integer InstrumentConfigId;
   
	//bi-directional many-to-one association to SearchSettingsSeqDatabaseMap
	@OneToMany(mappedBy="searchSetting")
	private Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps;

	//uni-directional many-to-many association to Enzyme
    @ManyToMany
	@JoinTable(
		name="used_enzyme"
		, joinColumns={
			@JoinColumn(name="search_settings_id")
			}
		, inverseJoinColumns={
			@JoinColumn(name="enzyme_id")
			}
		)
	private Set<Enzyme> enzymes;

	//bi-directional many-to-one association to UsedPtm
	@OneToMany(mappedBy="searchSetting")
	private Set<UsedPtm> usedPtms;

    public SearchSetting() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getIsDecoy() {
		return this.isDecoy;
	}

	public void setIsDecoy(Boolean isDecoy) {
		this.isDecoy = isDecoy;
	}

	public Integer getMaxMissedCleavages() {
		return this.maxMissedCleavages;
	}

	public void setMaxMissedCleavages(Integer maxMissedCleavages) {
		this.maxMissedCleavages = maxMissedCleavages;
	}

	public String getPeptideChargeStates() {
		return this.peptideChargeStates;
	}

	public void setPeptideChargeStates(String peptideChargeStates) {
		this.peptideChargeStates = peptideChargeStates;
	}

	public double getPeptideMassErrorTolerance() {
		return this.peptideMassErrorTolerance;
	}

	public void setPeptideMassErrorTolerance(double peptideMassErrorTolerance) {
		this.peptideMassErrorTolerance = peptideMassErrorTolerance;
	}

	public String getPeptideMassErrorToleranceUnit() {
		return this.peptideMassErrorToleranceUnit;
	}

	public void setPeptideMassErrorToleranceUnit(String peptideMassErrorToleranceUnit) {
		this.peptideMassErrorToleranceUnit = peptideMassErrorToleranceUnit;
	}

	public String getQuantitation() {
		return this.quantitation;
	}

	public void setQuantitation(String quantitation) {
		this.quantitation = quantitation;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getSoftwareName() {
		return this.softwareName;
	}

	public void setSoftwareName(String softwareName) {
		this.softwareName = softwareName;
	}

	public String getSoftwareVersion() {
		return this.softwareVersion;
	}

	public void setSoftwareVersion(String softwareVersion) {
		this.softwareVersion = softwareVersion;
	}

	public String getTaxonomy() {
		return this.taxonomy;
	}

	public void setTaxonomy(String taxonomy) {
		this.taxonomy = taxonomy;
	}
	
	public InstrumentConfig getInstrumentConfig() {
		return this.instrumentConfig;
	}

	public void setInstrumentConfig(InstrumentConfig instrumentConfig) {
		this.instrumentConfig = instrumentConfig;
	}
	
	public Integer getInstrumentConfigId() {
		return InstrumentConfigId;
	}

	public Set<SearchSettingsSeqDatabaseMap> getSearchSettingsSeqDatabaseMaps() {
		return this.searchSettingsSeqDatabaseMaps;
	}

	public void setSearchSettingsSeqDatabaseMaps(Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps) {
		this.searchSettingsSeqDatabaseMaps = searchSettingsSeqDatabaseMaps;
	}
	
	public Set<Enzyme> getEnzymes() {
		return this.enzymes;
	}

	public void setEnzymes(Set<Enzyme> enzymes) {
		this.enzymes = enzymes;
	}
	
	public Set<UsedPtm> getUsedPtms() {
		return this.usedPtms;
	}

	public void setUsedPtms(Set<UsedPtm> usedPtms) {
		this.usedPtms = usedPtms;
	}
	
}