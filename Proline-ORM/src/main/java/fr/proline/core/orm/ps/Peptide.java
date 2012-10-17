package fr.proline.core.orm.ps;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

/**
 * The persistent class for the peptide database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findPsPepsForSeq", query = "select p from fr.proline.core.orm.ps.Peptide p"
		+ " where upper(p.sequence) = :seq"),

	@NamedQuery(name = "findPsPepsForIds", query = "select p from fr.proline.core.orm.ps.Peptide p"
		+ " where p.id in :ids"),

	@NamedQuery(name = "findPsPeptForSeqPtmStr", query = "select p from fr.proline.core.orm.ps.Peptide p"
		+ " where (upper(p.sequence) = :seq) and (upper(p.ptmString) = :ptmStr)"),

	@NamedQuery(name = "findPsPeptForSeqWoPtm", query = "select p from fr.proline.core.orm.ps.Peptide p"
		+ " where (upper(p.sequence) = :seq)  and (p.ptmString is null)")

})
public class Peptide implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Integer id;

    @Column(name = "calculated_mass")
    private double calculatedMass;

    @Column(name = "ptm_string")
    private String ptmString;

    private String sequence;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to AtomLabel
    @ManyToOne
    @JoinColumn(name = "atom_label_id")
    private AtomLabel atomLabel;

    // bi-directional many-to-one association to PeptidePtm
    @OneToMany(mappedBy = "peptide")
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