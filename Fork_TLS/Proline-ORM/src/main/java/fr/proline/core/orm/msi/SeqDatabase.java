package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

import fr.proline.core.orm.pdi.SequenceDbConfig;
import fr.proline.core.orm.pdi.SequenceDbInstance;
import fr.proline.core.orm.pdi.SequenceDbRelease;
import fr.profi.util.DateUtils;
import fr.profi.util.StringUtils;

/**
 * The persistent class for the seq_database database table.
 * 
 */
@Entity
@NamedQueries({
@NamedQuery(name = "findMsiSeqDatabaseForNameAndFasta", query = "select sd from fr.proline.core.orm.msi.SeqDatabase sd"
	+ " where (sd.name = :name) and (sd.fastaFilePath = :fastaFilePath)"),
@NamedQuery(name = "findMsiSeqDatabaseForResultSet", query = "select sd from fr.proline.core.orm.msi.SeqDatabase sd"
		+ " where (sd.name = :name) and (sd.fastaFilePath = :fastaFilePath)") })
@Table(name = "seq_database")
public class SeqDatabase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "fasta_file_path")
    private String fastaFilePath;

    private String name;

    @Column(name = "release_date")
    private Timestamp releaseDate;

    @Column(name = "sequence_count")
    private Integer sequenceCount;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    // bi-directional many-to-one association to SearchSettingsSeqDatabaseMap
    @OneToMany(mappedBy = "seqDatabase")
    private Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps;

    public SeqDatabase() {
    }

    /**
     * Create a Msi SeqDatabase entity from Pdi SequenceDbInstance, SequenceDbConfig and SequenceDbRelease
     * entities.
     * 
     * @param pdiSequenceDbInstance
     *            SequenceDbInstance entity from pdiDb used to initialize Msi SeqDatabase fields (must not be
     *            <code>null</code>)
     */
    public SeqDatabase(final SequenceDbInstance pdiSequenceDbInstance) {

	if (pdiSequenceDbInstance == null) {
	    throw new IllegalArgumentException("PdiSequenceDbInstance is null");
	}

	final SequenceDbConfig pdiSequenceDbConfig = pdiSequenceDbInstance.getSequenceDbConfig();

	if (pdiSequenceDbConfig == null) {
	    throw new IllegalArgumentException("PdiSequenceDbConfig is null");
	}

	setFastaFilePath(pdiSequenceDbInstance.getFastaFilePath());
	setName(pdiSequenceDbConfig.getName());

	final SequenceDbRelease seqDbRelease = pdiSequenceDbInstance.getSequenceDbRelease();

	/* TODO : In MsiDb, ReleaseDate field must not be null */
	if (seqDbRelease == null) {
	    setVersion(Integer.toString(pdiSequenceDbInstance.getRevision()));
	} else {

	    final Date date = DateUtils.parseReleaseDate(seqDbRelease.getDate());
	    if (date != null) {
		setReleaseDate(new Timestamp(date.getTime()));
	    }

	    setVersion(seqDbRelease.getVersion());
	}

	setSequenceCount(pdiSequenceDbInstance.getSequenceCount());

	final String pdiSequenceDbInstanceProps = pdiSequenceDbInstance.getSerializedProperties();

	if (StringUtils.isEmpty(pdiSequenceDbInstanceProps)) {
	    setSerializedProperties(null);
	} else {
	    setSerializedProperties(pdiSequenceDbInstanceProps);
	}

    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getFastaFilePath() {
	return this.fastaFilePath;
    }

    public void setFastaFilePath(String fastaFilePath) {
	this.fastaFilePath = fastaFilePath;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public Timestamp getReleaseDate() {
	Timestamp result = null;

	if (releaseDate != null) { // Should not be null
	    result = (Timestamp) releaseDate.clone();
	}

	return result;
    }

    public void setReleaseDate(final Timestamp pReleaseDate) {

	if (pReleaseDate == null) {
	    throw new IllegalArgumentException("PReleaseDate is null");
	}

	releaseDate = (Timestamp) pReleaseDate.clone();
    }

    public Integer getSequenceCount() {
	return this.sequenceCount;
    }

    public void setSequenceCount(Integer sequenceCount) {
	this.sequenceCount = sequenceCount;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getVersion() {
	return this.version;
    }

    public void setVersion(String version) {
	this.version = version;
    }

    public Set<SearchSettingsSeqDatabaseMap> getSearchSettingsSeqDatabaseMaps() {
	return this.searchSettingsSeqDatabaseMaps;
    }

    public void setSearchSettingsSeqDatabaseMaps(
	    final Set<SearchSettingsSeqDatabaseMap> pSearchSettingsSeqDatabaseMaps) {
	this.searchSettingsSeqDatabaseMaps = pSearchSettingsSeqDatabaseMaps;
    }

    public void addSearchSettingsSeqDatabaseMap(final SearchSettingsSeqDatabaseMap seqDatabaseMap) {

	if (seqDatabaseMap != null) {
	    Set<SearchSettingsSeqDatabaseMap> seqDatabaseMaps = getSearchSettingsSeqDatabaseMaps();

	    if (seqDatabaseMaps == null) {
		seqDatabaseMaps = new HashSet<SearchSettingsSeqDatabaseMap>();

		setSearchSettingsSeqDatabaseMaps(seqDatabaseMaps);
	    }

	    seqDatabaseMaps.add(seqDatabaseMap);
	}

    }

    public void removeSearchSettingsSeqDatabaseMap(final SearchSettingsSeqDatabaseMap seqDatabaseMap) {

	final Set<SearchSettingsSeqDatabaseMap> seqDatabaseMaps = getSearchSettingsSeqDatabaseMaps();
	if (seqDatabaseMaps != null) {
	    seqDatabaseMaps.remove(seqDatabaseMap);
	}

    }

    @Override
    public String toString() {
	return new ToStringBuilder(this).append("name", getName()).append("version", getVersion())
		.append("release date", getReleaseDate()).toString();
    }

}
