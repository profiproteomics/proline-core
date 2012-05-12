package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Set;


/**
 * The persistent class for the seq_database database table.
 * 
 */
@Entity
@Table(name="seq_database")
public class SeqDatabase implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="fasta_file_path")
	private String fastaFilePath;

	private String name;

	@Column(name="release_date")
	private Timestamp releaseDate;

	@Column(name="sequence_count")
	private Integer sequenceCount;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String version;

	//bi-directional many-to-one association to SearchSettingsSeqDatabaseMap
	@OneToMany(mappedBy="seqDatabase")
	private Set<SearchSettingsSeqDatabaseMap> searchSettingsSeqDatabaseMaps;

    public SeqDatabase() {
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
	
	@Override
	public String toString() {
		return new ToStringBuilder(this).append("name", name).append("version", version).append("release date", releaseDate).toString();
	}
}