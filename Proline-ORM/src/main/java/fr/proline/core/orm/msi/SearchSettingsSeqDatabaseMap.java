package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * The persistent class for the search_settings_seq_database_map database table.
 * 
 */
@Entity
@Table(name="search_settings_seq_database_map")
public class SearchSettingsSeqDatabaseMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private SearchSettingsSeqDatabaseMapPK id;

	@Column(name="searched_sequences_count")
	private Integer searchedSequencesCount;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to SearchSetting
	//@JoinColumn(name="search_settings_id", updatable=false, insertable=false)
   @ManyToOne
   @JoinColumn(name="search_settings_id")
   @MapsId("searchSettingsId")
	private SearchSetting searchSetting;

	//bi-directional many-to-one association to SeqDatabase
   //@JoinColumn(name="seq_database_id", updatable=false, insertable=false)
    @ManyToOne
    @JoinColumn(name="seq_database_id")
    @MapsId("seqDatabaseId")
	private SeqDatabase seqDatabase;

    public SearchSettingsSeqDatabaseMap() {
   	 this.id = new SearchSettingsSeqDatabaseMapPK();
    }

	public SearchSettingsSeqDatabaseMapPK getId() {
		return this.id;
	}

	public void setId(SearchSettingsSeqDatabaseMapPK id) {
		this.id = id;
	}
	
	public Integer getSearchedSequencesCount() {
		return this.searchedSequencesCount;
	}

	public void setSearchedSequencesCount(Integer searchedSequencesCount) {
		this.searchedSequencesCount = searchedSequencesCount;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public SearchSetting getSearchSetting() {
		return this.searchSetting;
	}

	public void setSearchSetting(SearchSetting searchSetting) {
		this.searchSetting = searchSetting;
	}
	
	public SeqDatabase getSeqDatabase() {
		return this.seqDatabase;
	}

	public void setSeqDatabase(SeqDatabase seqDatabase) {
		this.seqDatabase = seqDatabase;
	}
	
	@Override
	public String toString() {
		return new ToStringBuilder(this).append("id", id).append("search setting", searchSetting).append("database", seqDatabase).toString();
	}
}