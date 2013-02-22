package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * The persistent class for the search_settings database table.
 * 
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(discriminatorType = DiscriminatorType.INTEGER, name = "id")
@Table(name = "search_settings")
public class SearchSetting implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum SoftwareName {
      MASCOT, OMSSA
    };

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Column(name = "is_decoy")
    private Boolean isDecoy;

    @Column(name = "max_missed_cleavages")
    private Integer maxMissedCleavages;

    @Column(name = "peptide_charge_states")
    private String peptideChargeStates;

    @Column(name = "peptide_mass_error_tolerance")
    private double peptideMassErrorTolerance;

    @Column(name = "peptide_mass_error_tolerance_unit")
    private String peptideMassErrorToleranceUnit;

    private String quantitation;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "software_name")
    private String softwareName;

    @Column(name = "software_version")
    private String softwareVersion;

    private String taxonomy;

    // uni-directional many-to-one association to InstrumentConfig
    @ManyToOne
    @JoinColumn(name = "instrument_config_id")
    private InstrumentConfig instrumentConfig;

    @Column(name = "instrument_config_id", insertable = false, updatable = false)
    private Integer InstrumentConfigId;

    // bi-directional many-to-one association to SearchSettingsSeqDatabaseMap
    @OneToMany(mappedBy = "searchSetting")
    private Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps;

    // uni-directional many-to-many association to Enzyme
    @ManyToMany
    @JoinTable(name = "used_enzyme", joinColumns = { @JoinColumn(name = "search_settings_id") }, inverseJoinColumns = { @JoinColumn(name = "enzyme_id") })
    private Set<Enzyme> enzymes;

    // bi-directional many-to-one association to UsedPtm
    @OneToMany(mappedBy = "searchSetting")
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

    public void setSearchSettingsSeqDatabaseMaps(
	    final Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps) {
	this.searchSettingsSeqDatabaseMaps = searchSettingsSeqDatabaseMaps;
    }

    public void addSearchSettingsSeqDatabaseMap(final SearchSettingsSeqDatabaseMap searchSettingsSeqDatabase) {

	if (searchSettingsSeqDatabase != null) {
	    Set<SearchSettingsSeqDatabaseMap> seqDatabaseMaps = getSearchSettingsSeqDatabaseMaps();

	    if (seqDatabaseMaps == null) {
		seqDatabaseMaps = new HashSet<SearchSettingsSeqDatabaseMap>();

		setSearchSettingsSeqDatabaseMaps(seqDatabaseMaps);
	    }

	    seqDatabaseMaps.add(searchSettingsSeqDatabase);
	}

    }

    public void removeSearchSettingsSeqDatabaseMap(
	    final SearchSettingsSeqDatabaseMap searchSettingsSeqDatabase) {
	final Set<SearchSettingsSeqDatabaseMap> seqDatabaseMaps = getSearchSettingsSeqDatabaseMaps();

	if (seqDatabaseMaps != null) {
	    seqDatabaseMaps.remove(searchSettingsSeqDatabase);
	}

    }

    public Set<Enzyme> getEnzymes() {
	return this.enzymes;
    }

    public void setEnzymes(final Set<Enzyme> enzymes) {
	this.enzymes = enzymes;
    }

    public void addEnzyme(final Enzyme enzyme) {

	if (enzyme != null) {
	    Set<Enzyme> enzs = getEnzymes();

	    if (enzs == null) {
		enzs = new HashSet<Enzyme>();

		setEnzymes(enzs);
	    }

	    enzs.add(enzyme);
	}

    }

    public void removeEnzyme(final Enzyme enzyme) {
	final Set<Enzyme> enzs = getEnzymes();

	if (enzs != null) {
	    enzs.remove(enzyme);
	}

    }

    public Set<UsedPtm> getUsedPtms() {
	return this.usedPtms;
    }

    public void setUsedPtms(final Set<UsedPtm> usedPtms) {
	this.usedPtms = usedPtms;
    }

    public void addUsedPtms(final UsedPtm usedPtm) {

	if (usedPtm != null) {
	    Set<UsedPtm> ptms = getUsedPtms();

	    if (ptms == null) {
		ptms = new HashSet<UsedPtm>();

		setUsedPtms(ptms);
	    }

	    ptms.add(usedPtm);
	}

    }

    public void removeUsedPtms(final UsedPtm usedPtm) {
	final Set<UsedPtm> ptms = getUsedPtms();

	if (ptms != null) {
	    ptms.remove(usedPtm);
	}

    }

    @Override
    public String toString() {
	return new ToStringBuilder(this).append("id", id).append("taxonomy", taxonomy)
		.append("miss cleav.", maxMissedCleavages).append("mass error", peptideMassErrorTolerance)
		.toString();
    }

}