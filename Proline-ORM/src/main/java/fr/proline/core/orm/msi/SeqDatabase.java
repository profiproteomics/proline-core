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
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

import fr.proline.core.orm.pdi.SequenceDbConfig;
import fr.proline.core.orm.pdi.SequenceDbInstance;
import fr.proline.core.orm.pdi.SequenceDbRelease;
import fr.proline.util.DateUtils;

/**
 * The persistent class for the seq_database database table.
 * 
 */
@Entity
@NamedQuery(name = "findMsiSeqDatabaseForNameAndFasta", query = "select sd from fr.proline.core.orm.msi.SeqDatabase sd"
		+ " where (sd.name = :name) and (sd.fastaFilePath = :fastaFilePath)")
@Table(name = "seq_database")
public class SeqDatabase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;

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
	 * Create a Msi SeqDatabase entity from Pdi SequenceDbInstance,
	 * SequenceDbConfig and SequenceDbRelease entities.
	 * 
	 * @param pdiSequenceDbInstance
	 *           SequenceDbInstance entity from pdiDb used to initialize Msi
	 *           SeqDatabase fields (must not be <code>null</code>)
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
			setVersion(pdiSequenceDbInstance.getRevision().toString());
		} else {

			final Date date = DateUtils.parseReleaseDate(seqDbRelease.getDate());
			if (date != null) {
				setReleaseDate(new Timestamp(date.getTime()));
			}

			setVersion(seqDbRelease.getVersion());
		}

		setSequenceCount(pdiSequenceDbInstance.getSequenceCount());
		setSerializedProperties(pdiSequenceDbInstance.getSerializedProperties());
	}

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
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
		return this.releaseDate;
	}

	public void setReleaseDate(Timestamp releaseDate) {
		this.releaseDate = releaseDate;
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

	public void setSearchSettingsSeqDatabaseMaps(Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps) {
		this.searchSettingsSeqDatabaseMaps = searchSettingsSeqDatabaseMaps;
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
		return new ToStringBuilder(this).append("name", name).append("version", version)
				.append("release date", releaseDate).toString();
	}

}