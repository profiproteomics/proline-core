package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the protein_match_seq_database_map database table.
 * 
 */
@Embeddable
public class ProteinMatchSeqDatabaseMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="protein_match_id")
	private Integer proteinMatchId;

	@Column(name="seq_database_id")
	private Integer seqDatabaseId;

    public ProteinMatchSeqDatabaseMapPK() {
    }
	public Integer getProteinMatchId() {
		return this.proteinMatchId;
	}
	public void setProteinMatchId(Integer proteinMatchId) {
		this.proteinMatchId = proteinMatchId;
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
		if (!(other instanceof ProteinMatchSeqDatabaseMapPK)) {
			return false;
		}
		ProteinMatchSeqDatabaseMapPK castOther = (ProteinMatchSeqDatabaseMapPK)other;
		return 
			this.proteinMatchId.equals(castOther.proteinMatchId)
			&& this.seqDatabaseId.equals(castOther.seqDatabaseId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.proteinMatchId.hashCode();
		hash = hash * prime + this.seqDatabaseId.hashCode();
		
		return hash;
    }
}