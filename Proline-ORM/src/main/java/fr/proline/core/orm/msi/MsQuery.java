package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the ms_query database table.
 * 
 */
@Entity
@Table(name="ms_query")
public class MsQuery implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer charge;

	@Column(name="initial_id")
	private Integer initialId;

	private double moz;

	@Column(name="msi_search_id")
	private Integer msiSearchId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to Spectrum
    @ManyToOne
	private Spectrum spectrum;

	//bi-directional many-to-one association to PeptideMatch
	@OneToMany(mappedBy="msQuery")
	private Set<PeptideMatch> peptideMatches;

    public MsQuery() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getCharge() {
		return this.charge;
	}

	public void setCharge(Integer charge) {
		this.charge = charge;
	}

	public Integer getInitialId() {
		return this.initialId;
	}

	public void setInitialId(Integer initialId) {
		this.initialId = initialId;
	}

	public double getMoz() {
		return this.moz;
	}

	public void setMoz(double moz) {
		this.moz = moz;
	}

	public Integer getMsiSearchId() {
		return this.msiSearchId;
	}

	public void setMsiSearchId(Integer msiSearchId) {
		this.msiSearchId = msiSearchId;
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
	
	public Set<PeptideMatch> getPeptideMatches() {
		return this.peptideMatches;
	}

	public void setPeptideMatches(Set<PeptideMatch> peptideMatches) {
		this.peptideMatches = peptideMatches;
	}
	
}