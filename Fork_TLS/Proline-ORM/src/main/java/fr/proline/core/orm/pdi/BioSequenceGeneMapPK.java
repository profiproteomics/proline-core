package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * The primary key class for the bio_sequence_gene_map database table.
 * 
 */
@Embeddable
public class BioSequenceGeneMapPK implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "bio_sequence_id")
    private long bioSequenceId;

    @Column(name = "gene_id")
    private long geneId;

    public BioSequenceGeneMapPK() {
    }

    public long getBioSequenceId() {
	return bioSequenceId;
    }

    public void setBioSequenceId(final long pBioSequenceId) {
	bioSequenceId = pBioSequenceId;
    }

    public long getGeneId() {
	return geneId;
    }

    public void setGeneId(final long pGeneId) {
	geneId = pGeneId;
    }

    @Override
    public boolean equals(final Object obj) {
	boolean result = false;

	if (obj == this) {
	    result = true;
	} else if (obj instanceof BioSequenceGeneMapPK) {
	    final BioSequenceGeneMapPK otherPK = (BioSequenceGeneMapPK) obj;

	    result = ((getBioSequenceId() == otherPK.getBioSequenceId()) && (getGeneId() == otherPK
		    .getGeneId()));
	}

	return result;
    }

    @Override
    public int hashCode() {
	return (Long.valueOf(getBioSequenceId()).hashCode() ^ Long.valueOf(getGeneId()).hashCode());
    }

}
