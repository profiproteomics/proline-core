package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name = "consensus_spectrum")
public class ConsensusSpectrum implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_mode")
    private String creationMode;

    @Column(name = "is_artificial")
    private boolean isArtificial;

    @Column(name = "normalized_elution_time")
    private Float normalizedElutionTime;

    @ManyToOne
    @JoinColumn(name = "peptide_id")
    private Peptide peptide;

    @Column(name = "precursor_calculated_moz")
    private double precursorCalculatedMoz;

    @Column(name = "precursor_charge")
    private int precursorCharge;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @ManyToOne
    @JoinColumn(name = "spectrum_id")
    private Spectrum spectrum;

    public ConsensusSpectrum() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getCreationMode() {
	return this.creationMode;
    }

    public void setCreationMode(String creationMode) {
	this.creationMode = creationMode;
    }

    public boolean getIsArtificial() {
	return isArtificial;
    }

    public void setIsArtificial(final boolean pIsArtificial) {
	isArtificial = pIsArtificial;
    }

    public Float getNormalizedElutionTime() {
	return normalizedElutionTime;
    }

    public void setNormalizedElutionTime(final Float pNormalizedElutionTime) {
	normalizedElutionTime = pNormalizedElutionTime;
    }

    public Peptide getPeptide() {
	return this.peptide;
    }

    public void setPeptide(Peptide peptide) {
	this.peptide = peptide;
    }

    public double getPrecursorCalculatedMoz() {
	return this.precursorCalculatedMoz;
    }

    public void setPrecursorCalculatedMoz(double precursorCalculatedMoz) {
	this.precursorCalculatedMoz = precursorCalculatedMoz;
    }

    public int getPrecursorCharge() {
	return precursorCharge;
    }

    public void setPrecursorCharge(final int pPrecursorCharge) {
	precursorCharge = pPrecursorCharge;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public Spectrum getSpectrum() {
	return this.spectrum;
    }

    public void setSpectrum(Spectrum spectrum) {
	this.spectrum = spectrum;
    }

}
