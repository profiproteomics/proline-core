package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the protein_identifier database table.
 * 
 */
@Entity
@Table(name="protein_identifier")
public class ProteinIdentifier implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="is_ac_number")
	private Boolean isAcNumber;

	@Column(name="is_active")
	private Boolean isActive;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String value;

	//bi-directional many-to-one association to BioSequence
    @ManyToOne
	@JoinColumn(name="bio_sequence_id")
	private BioSequence bioSequence;

	//uni-directional many-to-one association to SequenceDbConfig
    @ManyToOne
	@JoinColumn(name="database_type")
	private SequenceDbConfig sequenceDbConfig;

	//uni-directional many-to-one association to Taxon
    @ManyToOne
	private Taxon taxon;

    public ProteinIdentifier() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getIsAcNumber() {
		return this.isAcNumber;
	}

	public void setIsAcNumber(Boolean isAcNumber) {
		this.isAcNumber = isAcNumber;
	}

	public Boolean getIsActive() {
		return this.isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public BioSequence getBioSequence() {
		return this.bioSequence;
	}

	public void setBioSequence(BioSequence bioSequence) {
		this.bioSequence = bioSequence;
	}
	
	public SequenceDbConfig getSequenceDbConfig() {
		return this.sequenceDbConfig;
	}

	public void setSequenceDbConfig(SequenceDbConfig sequenceDbConfig) {
		this.sequenceDbConfig = sequenceDbConfig;
	}
	
	public Taxon getTaxon() {
		return this.taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
	}
	
}