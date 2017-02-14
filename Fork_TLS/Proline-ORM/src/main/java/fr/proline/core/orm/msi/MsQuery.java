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
    private long id;

    private int charge;

    @Column(name = "initial_id")
    private int initialId;

    private double moz;

    @ManyToOne
    @JoinColumn(name = "msi_search_id", nullable = false)
    private MsiSearch msiSearch;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to Spectrum
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spectrum_id")
    private Spectrum spectrum;

    // bi-directional many-to-one association to PeptideMatch
    @OneToMany(mappedBy = "msQuery")
    private Set<PeptideMatch> peptideMatches;

    
    public MsQuery() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getCharge() {
	return charge;
    }

    public void setCharge(final int pCharge) {
	charge = pCharge;
    }

    public int getInitialId() {
	return initialId;
    }

    public void setInitialId(final int pInitialId) {
	initialId = pInitialId;
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

    public void setPeptideMatches(final Set<PeptideMatch> pPeptideMatches) {
	peptideMatches = pPeptideMatches;
    }

    public void addPeptideMatch(final PeptideMatch peptideMatch) {

	if (peptideMatch != null) {
	    Set<PeptideMatch> localPeptideMatches = getPeptideMatches();

	    if (localPeptideMatches == null) {
		localPeptideMatches = new HashSet<PeptideMatch>();

		setPeptideMatches(localPeptideMatches);
	    }

	    localPeptideMatches.add(peptideMatch);
	}

    }

    public void removePeptideMatch(final PeptideMatch peptideMatch) {

	final Set<PeptideMatch> localPeptideMatches = getPeptideMatches();
	if (localPeptideMatches != null) {
	    localPeptideMatches.remove(peptideMatch);
	}

    }


}
