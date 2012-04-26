package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name="consensus_spectrum")
public class ConsensusSpectrum implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_mode")
	private String creationMode;

	@Column(name="is_artificial")
	private Boolean isArtificial;

	@Column(name="normalized_elution_time")
	private float normalizedElutionTime;

	@ManyToOne
	@JoinColumn(name="peptide_id")
	private Peptide peptide;

	@Column(name="precursor_calculated_moz")
	private double precursorCalculatedMoz;

	@Column(name="precursor_charge")
	private Integer precursorCharge;

	@Column(name="serialized_properties")
	private String serializedProperties;

	@ManyToOne
	@JoinColumn(name = "spectrum_id")
	private Spectrum spectrum;

    public ConsensusSpectrum() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getCreationMode() {
		return this.creationMode;
	}

	public void setCreationMode(String creationMode) {
		this.creationMode = creationMode;
	}

	public Boolean getIsArtificial() {
		return this.isArtificial;
	}

	public void setIsArtificial(Boolean isArtificial) {
		this.isArtificial = isArtificial;
	}

	public float getNormalizedElutionTime() {
		return this.normalizedElutionTime;
	}

	public void setNormalizedElutionTime(float normalizedElutionTime) {
		this.normalizedElutionTime = normalizedElutionTime;
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

	public Integer getPrecursorCharge() {
		return this.precursorCharge;
	}

	public void setPrecursorCharge(Integer precursorCharge) {
		this.precursorCharge = precursorCharge;
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