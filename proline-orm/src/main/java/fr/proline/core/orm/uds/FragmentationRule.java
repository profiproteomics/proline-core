package fr.proline.core.orm.uds;

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
 * The persistent class for the fragmentation_rule database table.
 * 
 */
@Entity
@Table(name = "fragmentation_rule")
public class FragmentationRule implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String description;

	@Column(name = "fragment_charge")
	private Integer fragmentCharge;

	@Column(name = "fragment_max_moz")
	private Float fragmentMaxMoz;

	@Column(name = "fragment_residue_constraint")
	private String fragmentResidueConstraint;

	@Column(name = "precursor_min_charge")
	private Integer precursorMinCharge;

	@Column(name = "required_series_id")
	private Long requiredSeriesId;

	@Column(name = "required_series_quality_level")
	private String requiredSeriesQualityLevel;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// uni-directional many-to-one association to FragmentationSeries
	@ManyToOne
	@JoinColumn(name = "fragment_series_id")
	private FragmentationSeries fragmentationSeries;

	public FragmentationRule() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
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

	public Float getFragmentMaxMoz() {
		return this.fragmentMaxMoz;
	}

	public void setFragmentMaxMoz(Float fragmentMaxMoz) {
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

	public Long getRequiredSeriesId() {
		return requiredSeriesId;
	}

	public void setRequiredSeriesId(final Long pRequiredSeriesId) {
		requiredSeriesId = pRequiredSeriesId;
	}

	public String getRequiredSeriesQualityLevel() {
		return this.requiredSeriesQualityLevel;
	}

	public void setRequiredSeriesQualityLevel(String requiredSeriesQualityLevel) {
		this.requiredSeriesQualityLevel = requiredSeriesQualityLevel;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public FragmentationSeries getFragmentationSeries() {
		return this.fragmentationSeries;
	}

	public void setFragmentationSeries(FragmentationSeries fragmentationSeries) {
		this.fragmentationSeries = fragmentationSeries;
	}

	@Override
	public String toString() {
		return id + "_" + description;
	}
}
