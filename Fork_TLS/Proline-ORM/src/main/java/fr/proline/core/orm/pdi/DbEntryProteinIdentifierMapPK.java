package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the bio_sequence_gene_map database table.
 * 
 */
@Embeddable
public class DbEntryProteinIdentifierMapPK implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "seq_db_entry_id")
    private long sequenceDbEntryId;

    @Column(name = "protein_identifier_id")
    private long proteinIdentifierId;

    public DbEntryProteinIdentifierMapPK() {
    }

    void setSequenceDbEntryId(final long pSequenceDbEntryId) {
	sequenceDbEntryId = pSequenceDbEntryId;
    }

    public long getSequenceDbEntryId() {
	return sequenceDbEntryId;
    }

    void setProteinIdentifierId(final long pProteinIdentifierId) {
	proteinIdentifierId = pProteinIdentifierId;
    }

    public long getProteinIdentifierId() {
	return proteinIdentifierId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof DbEntryProteinIdentifierMapPK) {
	    final DbEntryProteinIdentifierMapPK otherPK = (DbEntryProteinIdentifierMapPK) obj;

	    result = ((getSequenceDbEntryId() == otherPK.getSequenceDbEntryId()) && (getProteinIdentifierId() == otherPK
		    .getSequenceDbEntryId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getSequenceDbEntryId()).hashCode() ^ Long.valueOf(getProteinIdentifierId())
		.hashCode());
    }

}
