package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * The persistent class for the search_settings_seq_database_map database table.
 * 
 */
@Entity
@Table(name = "search_settings_seq_database_map")
public class SearchSettingsSeqDatabaseMap implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private SearchSettingsSeqDatabaseMapPK id;

    @Column(name = "searched_sequences_count")
    private int searchedSequencesCount;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to SearchSetting
    @ManyToOne
    @JoinColumn(name = "search_settings_id")
    @MapsId("searchSettingsId")
    private SearchSetting searchSetting;

    // bi-directional many-to-one association to SeqDatabase
    @ManyToOne
    @JoinColumn(name = "seq_database_id")
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

    public int getSearchedSequencesCount() {
	return searchedSequencesCount;
    }

    public void setSearchedSequencesCount(final int pSearchedSequencesCount) {
	searchedSequencesCount = pSearchedSequencesCount;
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
	return new ToStringBuilder(this).append("id", getId()).append("search setting", getSearchSetting())
		.append("database", getSearchedSequencesCount()).toString();
    }

}
