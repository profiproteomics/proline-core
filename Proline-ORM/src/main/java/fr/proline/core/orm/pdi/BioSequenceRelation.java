package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the bio_sequence_relation database table.
 * 
 */
@Entity
@Table(name="bio_sequence_relation")
public class BioSequenceRelation implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private BioSequenceRelationPK id;

	@Column(name="frame_number")
	private Integer frameNumber;

	//uni-directional many-to-one association to BioSequence
    @ManyToOne
	@JoinColumn(name="na_sequence_id", updatable=false, insertable=false)
	private BioSequence nucleotidsSequence;

	//uni-directional many-to-one association to BioSequence
    @ManyToOne
	@JoinColumn(name="aa_sequence_id", updatable=false, insertable=false)
	private BioSequence aminoAcidsSequence;

    public BioSequenceRelation() {
    }

	public BioSequenceRelationPK getId() {
		return this.id;
	}

	public void setId(BioSequenceRelationPK id) {
		this.id = id;
	}
	
	public Integer getFrameNumber() {
		return this.frameNumber;
	}

	public void setFrameNumber(Integer frameNumber) {
		this.frameNumber = frameNumber;
	}

	public BioSequence getNucleotidsSequence() {
		return this.nucleotidsSequence;
	}

	public void setNucleotidsSequence(BioSequence nucleotidsSequence) {
		this.nucleotidsSequence = nucleotidsSequence;
	}
	
	public BioSequence getAminoAcidsSequence() {
		return this.aminoAcidsSequence;
	}

	public void setAminoAcidsSequence(BioSequence aminoAcidsSequence) {
		this.aminoAcidsSequence = aminoAcidsSequence;
	}
	
}