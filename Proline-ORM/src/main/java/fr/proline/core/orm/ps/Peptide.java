package fr.proline.core.orm.ps;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the peptide database table.
 * 
 */
@Entity
@NamedQuery(name="findPepsBySeq",
query="select p from Peptide p where UPPER(p.sequence) = :seq ")
public class Peptide implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="calculated_mass")
	private double calculatedMass;

	@Column(name="ptm_string")
	private String ptmString;

	private String sequence;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to AtomLabel
    @ManyToOne
	@JoinColumn(name="atom_label_id")
	private AtomLabel atomLabel;

	//bi-directional many-to-one association to PeptidePtm
	@OneToMany(mappedBy="peptide")
	private Set<PeptidePtm> ptms;

    public Peptide() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public double getCalculatedMass() {
		return this.calculatedMass;
	}

	public void setCalculatedMass(double calculatedMass) {
		this.calculatedMass = calculatedMass;
	}

	public String getPtmString() {
		return this.ptmString;
	}

	public void setPtmString(String ptmString) {
		this.ptmString = ptmString;
	}

	public String getSequence() {
		return this.sequence;
	}

	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public AtomLabel getAtomLabel() {
		return this.atomLabel;
	}

	public void setAtomLabel(AtomLabel atomLabel) {
		this.atomLabel = atomLabel;
	}
	
	public Set<PeptidePtm> getPtms() {
		return this.ptms;
	}

	public void setPtms(Set<PeptidePtm> ptms) {
		this.ptms = ptms;
	}
	
}