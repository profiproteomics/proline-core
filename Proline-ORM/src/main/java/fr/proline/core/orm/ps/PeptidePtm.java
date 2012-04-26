package fr.proline.core.orm.ps;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peptide_ptm database table.
 * 
 */
@Entity
@Table(name="peptide_ptm")
public class PeptidePtm implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="average_mass")
	private double averageMass;

	@Column(name="mono_mass")
	private double monoMass;

	@Column(name="seq_position")
	private Integer seqPosition;

	//uni-directional many-to-one association to AtomLabel
    @ManyToOne
	@JoinColumn(name="atom_label_id")
	private AtomLabel atomLabel;

	//bi-directional many-to-one association to Peptide
    @ManyToOne
	@JoinColumn(name="peptide_id")
	private Peptide peptide;

	//uni-directional many-to-one association to PtmSpecificity
    @ManyToOne
	@JoinColumn(name="ptm_specificity_id")
	private PtmSpecificity specificity;

    public PeptidePtm() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public double getAverageMass() {
		return this.averageMass;
	}

	public void setAverageMass(double averageMass) {
		this.averageMass = averageMass;
	}

	public double getMonoMass() {
		return this.monoMass;
	}

	public void setMonoMass(double monoMass) {
		this.monoMass = monoMass;
	}

	public Integer getSeqPosition() {
		return this.seqPosition;
	}

	public void setSeqPosition(Integer seqPosition) {
		this.seqPosition = seqPosition;
	}

	public AtomLabel getAtomLabel() {
		return this.atomLabel;
	}

	public void setAtomLabel(AtomLabel atomLabel) {
		this.atomLabel = atomLabel;
	}
	
	public Peptide getPeptide() {
		return this.peptide;
	}

	public void setPeptide(Peptide peptide) {
		this.peptide = peptide;
	}
	
	public PtmSpecificity getSpecificity() {
		return this.specificity;
	}

	public void setSpecificity(PtmSpecificity specificity) {
		this.specificity = specificity;
	}
	
}