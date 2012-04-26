package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the search_settings_seq_database_map database table.
 * 
 */
@Embeddable
public class SearchSettingsSeqDatabaseMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="search_settings_id")
	private Integer searchSettingsId;

	@Column(name="seq_database_id")
	private Integer seqDatabaseId;

    public SearchSettingsSeqDatabaseMapPK() {
    }
	public Integer getSearchSettingsId() {
		return this.searchSettingsId;
	}
	public void setSearchSettingsId(Integer searchSettingsId) {
		this.searchSettingsId = searchSettingsId;
	}
	public Integer getSeqDatabaseId() {
		return this.seqDatabaseId;
	}
	public void setSeqDatabaseId(Integer seqDatabaseId) {
		this.seqDatabaseId = seqDatabaseId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SearchSettingsSeqDatabaseMapPK)) {
			return false;
		}
		SearchSettingsSeqDatabaseMapPK castOther = (SearchSettingsSeqDatabaseMapPK)other;
		return 
			this.searchSettingsId.equals(castOther.searchSettingsId)
			&& this.seqDatabaseId.equals(castOther.seqDatabaseId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.searchSettingsId.hashCode();
		hash = hash * prime + this.seqDatabaseId.hashCode();
		
		return hash;
    }
}