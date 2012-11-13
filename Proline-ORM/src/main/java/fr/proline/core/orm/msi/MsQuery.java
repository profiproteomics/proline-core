package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * The persistent class for the ms_query database table.
 * 
 */
@Entity
@Table(name = "ms_query")
public class MsQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private Integer charge;

    @Column(name = "initial_id")
    private Integer initialId;

    private double moz;

    @ManyToOne
    @JoinColumn(name = "msi_search_id", nullable = false)
    private MsiSearch msiSearch;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to Spectrum
    @ManyToOne(fetch = FetchType.LAZY)
    private Spectrum spectrum;

    // bi-directional many-to-one association to PeptideMatch
    @OneToMany(mappedBy = "msQuery")
    private Set<PeptideMatch> peptideMatches;

 // Transient Variable not saved in database
  	@Transient private boolean isSpectrumSet = false;
    
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

    public void setMsiSearch(final MsiSearch search) {
	msiSearch = search;
    }

    public MsiSearch getMsiSearch() {
	return msiSearch;
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

    public void addPeptideMatch(final PeptideMatch peptideMatch) {

	if (peptideMatch != null) {
	    Set<PeptideMatch> peptMatches = getPeptideMatches();

	    if (peptMatches == null) {
		peptMatches = new HashSet<PeptideMatch>();

		setPeptideMatches(peptMatches);
	    }

	    peptMatches.add(peptideMatch);
	}

    }

    public void removePeptideMatch(final PeptideMatch peptideMatch) {

	final Set<PeptideMatch> peptMatches = getPeptideMatches();
	if (peptMatches != null) {
	    peptMatches.remove(peptideMatch);
	}

    }

    public boolean getTransientIsSpectrumSet() {
		return isSpectrumSet;
	}

	public void setTransientIsSpectrumSet(boolean isSpectrumSet) {
		this.isSpectrumSet = isSpectrumSet;
	}
    
    
}