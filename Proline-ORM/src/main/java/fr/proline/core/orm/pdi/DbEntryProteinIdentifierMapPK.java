package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the bio_sequence_gene_map database table.
 * 
 */
@Embeddable
public class DbEntryProteinIdentifierMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="seq_db_entry_id")
	private Integer sequenceDbEntryId;

	@Column(name="protein_identifier_id")
	private Integer proteinIdentifierId;

    public DbEntryProteinIdentifierMapPK() {
    }
    
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DbEntryProteinIdentifierMapPK)) {
			return false;
		}
		DbEntryProteinIdentifierMapPK castOther = (DbEntryProteinIdentifierMapPK)other;
		return 
			this.sequenceDbEntryId.equals(castOther.sequenceDbEntryId)
			&& this.proteinIdentifierId.equals(castOther.proteinIdentifierId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.sequenceDbEntryId.hashCode();
		hash = hash * prime + this.proteinIdentifierId.hashCode();
		
		return hash;
    }
}