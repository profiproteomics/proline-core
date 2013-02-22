package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The primary key class for the bio_sequence_gene_map database table.
 * 
 */
@Embeddable
public class BioSequenceGeneMapPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;

	@Column(name="bio_sequence_id")
	private Integer bioSequenceId;

	@Column(name="gene_id")
	private Integer geneId;

    public BioSequenceGeneMapPK() {
    }
	public Integer getBioSequenceId() {
		return this.bioSequenceId;
	}
	public void setBioSequenceId(Integer bioSequenceId) {
		this.bioSequenceId = bioSequenceId;
	}
	public Integer getGeneId() {
		return this.geneId;
	}
	public void setGeneId(Integer geneId) {
		this.geneId = geneId;
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BioSequenceGeneMapPK)) {
			return false;
		}
		BioSequenceGeneMapPK castOther = (BioSequenceGeneMapPK)other;
		return 
			this.bioSequenceId.equals(castOther.bioSequenceId)
			&& this.geneId.equals(castOther.geneId);

    }
    
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.bioSequenceId.hashCode();
		hash = hash * prime + this.geneId.hashCode();
		
		return hash;
    }
}