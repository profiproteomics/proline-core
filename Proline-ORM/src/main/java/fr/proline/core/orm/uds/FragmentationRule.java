package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the fragmentation_rule database table.
 * 
 */
@Entity
@Table(name="fragmentation_rule")
public class FragmentationRule implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String description;

	@Column(name="fragment_charge")
	private Integer fragmentCharge;

	@Column(name="fragment_max_moz")
	private float fragmentMaxMoz;

	@Column(name="fragment_residue_constraint")
	private String fragmentResidueConstraint;

	@Column(name="precursor_min_charge")
	private Integer precursorMinCharge;

	@Column(name="required_serie_id")
	private Integer requiredSerieId;

	@Column(name="required_serie_quality_level")
	private String requiredSerieQualityLevel;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to TheoreticalFragment
    @ManyToOne
	@JoinColumn(name="theoretical_fragment_id")
	private TheoreticalFragment theoreticalFragment;

    public FragmentationRule() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getFragmentCharge() {
		return this.fragmentCharge;
	}

	public void setFragmentCharge(Integer fragmentCharge) {
		this.fragmentCharge = fragmentCharge;
	}

	public float getFragmentMaxMoz() {
		return this.fragmentMaxMoz;
	}

	public void setFragmentMaxMoz(float fragmentMaxMoz) {
		this.fragmentMaxMoz = fragmentMaxMoz;
	}

	public String getFragmentResidueConstraint() {
		return this.fragmentResidueConstraint;
	}

	public void setFragmentResidueConstraint(String fragmentResidueConstraint) {
		this.fragmentResidueConstraint = fragmentResidueConstraint;
	}

	public Integer getPrecursorMinCharge() {
		return this.precursorMinCharge;
	}

	public void setPrecursorMinCharge(Integer precursorMinCharge) {
		this.precursorMinCharge = precursorMinCharge;
	}

	public Integer getRequiredSerieId() {
		return this.requiredSerieId;
	}

	public void setRequiredSerieId(Integer requiredSerieId) {
		this.requiredSerieId = requiredSerieId;
	}

	public String getRequiredSerieQualityLevel() {
		return this.requiredSerieQualityLevel;
	}

	public void setRequiredSerieQualityLevel(String requiredSerieQualityLevel) {
		this.requiredSerieQualityLevel = requiredSerieQualityLevel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public TheoreticalFragment getTheoreticalFragment() {
		return this.theoreticalFragment;
	}

	public void setTheoreticalFragment(TheoreticalFragment theoreticalFragment) {
		this.theoreticalFragment = theoreticalFragment;
	}
	
}