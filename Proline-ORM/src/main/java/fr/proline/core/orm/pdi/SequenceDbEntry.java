package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the seq_db_entry database table.
 * 
 */
@Entity
@Table(name="seq_db_entry")
public class SequenceDbEntry implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private String identifier;

	@Column(name="is_active")
	private Boolean isActive;

	private String name;

	@Column(name="ref_file_block_length")
	private Integer refFileBlockLength;

	@Column(name="ref_file_block_start")
	private Integer refFileBlockStart;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String version;

	// uni-directional many-to-one association to BioSequence
	@ManyToOne
	@JoinColumn(name = "bio_sequence_id")
	private BioSequence bioSequence;

	// uni-directional many-to-one association to SequenceDbConfig
	@ManyToOne
	@JoinColumn(name = "database_type")
	private SequenceDbConfig sequenceDbConfig;

	// uni-directional many-to-one association to SequenceDbInstance
	@ManyToOne
	@JoinColumn(name = "seq_db_instance_id")
	private SequenceDbInstance sequenceDbInstance;

	// uni-directional many-to-one association to Taxon
	@ManyToOne
	@JoinColumn(name = "taxon_id",  insertable = false, updatable = false)
	private Taxon taxon;

	@Column(name = "taxon_id")
	private Integer taxonId;

	public SequenceDbEntry() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public Boolean getIsActive() {
		return this.isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getRefFileBlockLength() {
		return this.refFileBlockLength;
	}

	public void setRefFileBlockLength(Integer refFileBlockLength) {
		this.refFileBlockLength = refFileBlockLength;
	}

	public Integer getRefFileBlockStart() {
		return this.refFileBlockStart;
	}

	public void setRefFileBlockStart(Integer refFileBlockStart) {
		this.refFileBlockStart = refFileBlockStart;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
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
	
	public SequenceDbInstance getSequenceDbInstance() {
		return this.sequenceDbInstance;
	}

	public void setSequenceDbInstance(SequenceDbInstance sequenceDbInstance) {
		this.sequenceDbInstance = sequenceDbInstance;
	}
	
	public Taxon getTaxon() {
		return this.taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
	}
	
	public Integer getTaxonId() {
		return taxonId;
	}

	public void setTaxonId(Integer taxonId) {
		this.taxonId = taxonId;
	}
	
}