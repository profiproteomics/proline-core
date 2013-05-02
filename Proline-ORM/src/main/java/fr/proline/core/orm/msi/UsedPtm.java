package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the used_ptm database table.
 * 
 */
@Entity
@Table(name="used_ptm")
public class UsedPtm implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private UsedPtmPK id;

	@Column(name="is_fixed")
	private Boolean isFixed;

	@Column(name="short_name")
	private String shortName;

	//bi-directional many-to-one association to PtmSpecificity
   @ManyToOne
	@JoinColumn(name="ptm_specificity_id")
   @MapsId("ptmSpecificityId")
	private PtmSpecificity ptmSpecificity;

	//bi-directional many-to-one association to SearchSetting
   @ManyToOne
	@JoinColumn(name="search_settings_id")
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
	
	public Boolean getIsFixed() {
		return this.isFixed;
	}

	public void setIsFixed(Boolean isFixed) {
		this.isFixed = isFixed;
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