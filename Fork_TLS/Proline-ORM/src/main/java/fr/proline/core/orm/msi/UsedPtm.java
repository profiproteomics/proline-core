package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

/**
 * The persistent class for the used_ptm database table.
 * 
 */
@Entity
@Table(name = "used_ptm")
public class UsedPtm implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private UsedPtmPK id;

    @Column(name = "is_fixed")
    private boolean isFixed;

    @Column(name = "short_name")
    private String shortName;

    // bi-directional many-to-one association to PtmSpecificity
    @ManyToOne
    @JoinColumn(name = "ptm_specificity_id")
    @MapsId("ptmSpecificityId")
    private PtmSpecificity ptmSpecificity;

    // bi-directional many-to-one association to SearchSetting
    @ManyToOne
    @JoinColumn(name = "search_settings_id")
    @MapsId("searchSettingsId")
    private SearchSetting searchSetting;

    public UsedPtm() {
	this.id = new UsedPtmPK();
    }

    public UsedPtmPK getId() {
	return this.id;
    }

    public void setId(UsedPtmPK id) {
	this.id = id;
    }

    public boolean getIsFixed() {
	return isFixed;
    }

    public void setIsFixed(final boolean pIsFixed) {
	isFixed = pIsFixed;
    }

    public String getShortName() {
	return this.shortName;
    }

    public void setShortName(String shortName) {
	this.shortName = shortName;
    }

    public PtmSpecificity getPtmSpecificity() {
	return this.ptmSpecificity;
    }

    public void setPtmSpecificity(PtmSpecificity ptmSpecificity) {
	this.ptmSpecificity = ptmSpecificity;
    }

    public SearchSetting getSearchSetting() {
	return this.searchSetting;
    }

    public void setSearchSetting(SearchSetting searchSetting) {
	this.searchSetting = searchSetting;
    }

}
