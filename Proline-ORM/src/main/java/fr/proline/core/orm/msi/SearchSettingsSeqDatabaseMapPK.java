package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the search_settings_seq_database_map database table.
 * 
 */
@Embeddable
public class SearchSettingsSeqDatabaseMapPK implements Serializable {

	// default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name = "search_settings_id")
	private long searchSettingsId;

	@Column(name = "seq_database_id")
	private long seqDatabaseId;

	public SearchSettingsSeqDatabaseMapPK() {
	}

	public long getSearchSettingsId() {
		return searchSettingsId;
	}

	public void setSearchSettingsId(final long pSearchSettingsId) {
		searchSettingsId = pSearchSettingsId;
	}

	public long getSeqDatabaseId() {
		return seqDatabaseId;
	}

	public void setSeqDatabaseId(final long pSeqDatabaseId) {
		seqDatabaseId = pSeqDatabaseId;
	}

	@Override
	public boolean equals(final Object obj) {
		boolean result = false;

		if (obj == this) {
			result = true;
		} else if (obj instanceof SearchSettingsSeqDatabaseMapPK) {
			final SearchSettingsSeqDatabaseMapPK otherPK = (SearchSettingsSeqDatabaseMapPK) obj;

			result = ((getSearchSettingsId() == otherPK.getSearchSettingsId()) && (getSeqDatabaseId() == otherPK
				.getSeqDatabaseId()));
		}

		return result;
	}

	public int hashCode() {
		return (Long.valueOf(getSearchSettingsId()).hashCode() ^ Long.valueOf(getSeqDatabaseId()).hashCode());
	}

}
