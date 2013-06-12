package fr.proline.core.orm.ps;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the ptm_evidence database table.
 * 
 */
@Entity
@NamedQuery(name = "findPtmEvidenceByPtmAndType", query = "select pe from PtmEvidence pe where pe.ptm.id = :ptm_id and pe.type = :type")
@Table(name = "ptm_evidence")
public class PtmEvidence implements Serializable {

    public enum Type {
	Precursor, Artefact, NeutralLoss, PepNeutralLoss
    };

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private long id;

    @Column(name = "average_mass")
    private double averageMass;

    private String composition;

    @Column(name = "is_required")
    private boolean isRequired;

    @Column(name = "mono_mass")
    private double monoMass;

    @Enumerated(EnumType.STRING)
    private Type type;

    // bi-directional many-to-one association to Ptm
    @ManyToOne
    @JoinColumn(name = "ptm_id")
    private Ptm ptm;

    // bi-directional one-to-one association to PtmSpecificity
    @JoinColumn(name = "specificity_id")
    @ManyToOne
    private PtmSpecificity specificity;

    public PtmEvidence() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public double getAverageMass() {
	return this.averageMass;
    }

    public void setAverageMass(double averageMass) {
	this.averageMass = averageMass;
    }

    public String getComposition() {
	return this.composition;
    }

    public void setComposition(String composition) {
	this.composition = composition;
    }

    public boolean getIsRequired() {
	return isRequired;
    }

    public void setIsRequired(final boolean pIsRequired) {
	isRequired = pIsRequired;
    }

    public double getMonoMass() {
	return this.monoMass;
    }

    public void setMonoMass(double monoMass) {
	this.monoMass = monoMass;
    }

    public Type getType() {
	return this.type;
    }

    public void setType(Type type) {
	this.type = type;
    }

    public Ptm getPtm() {
	return this.ptm;
    }

    public void setPtm(Ptm ptm) {
	this.ptm = ptm;
    }

    public PtmSpecificity getSpecificity() {
	return specificity;
    }

    public void setSpecificity(PtmSpecificity specificity) {
	this.specificity = specificity;
    }

}
