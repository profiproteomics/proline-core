package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;

/**
 * The persistent class for the quantitation_fraction database table.
 * 
 */
@Entity
@Table(name="quantitation_fraction")
public class QuantitationFraction implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="lcms_map_set_id")
	private Integer lcmsMapSetId;

	private String name;

	private Integer number;

	@Column(name="quant_result_summary_id")
	private Integer quantResultSummaryId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to QuantChannel
	@OneToMany(mappedBy="quantitationFraction")
	@OrderBy("id ASC") // TODO: add a number column to the quant_channel table
	private List<QuantitationChannel> quantitationChannels;

	//bi-directional many-to-one association to Quantitation
    @ManyToOne
	private Quantitation quantitation;

    public QuantitationFraction() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getLcmsMapSetId() {
		return this.lcmsMapSetId;
	}

	public void setLcmsMapSetId(Integer lcmsMapSetId) {
		this.lcmsMapSetId = lcmsMapSetId;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public Integer getQuantResultSummaryId() {
		return this.quantResultSummaryId;
	}

	public void setQuantResultSummaryId(Integer quantResultSummaryId) {
		this.quantResultSummaryId = quantResultSummaryId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public List<QuantitationChannel> getQuantitationChannels() {
		return this.quantitationChannels;
	}

	public void setQuantitationChannels(List<QuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
	}
	
	public Quantitation getQuantitation() {
		return this.quantitation;
	}

	public void setQuantitation(Quantitation quantitation) {
		this.quantitation = quantitation;
	}
	
}