package fr.proline.core.orm.pdi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the seq_db_entry database table.
 * 
 */
@Entity
@Table(name = "seq_db_entry")
public class SequenceDbEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String identifier;

    @Column(name = "is_active")
    private boolean isActive;

    private String name;

    @Column(name = "ref_file_block_length")
    private Integer refFileBlockLength;

    @Column(name = "ref_file_block_start")
    private Long refFileBlockStart;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    // uni-directional many-to-one association to BioSequence
    @ManyToOne
    @JoinColumn(name = "bio_sequence_id")
    private BioSequence bioSequence;

    // uni-directional many-to-one association to SequenceDbConfig
    @ManyToOne
    @JoinColumn(name = "seq_db_config_id")
    private SequenceDbConfig sequenceDbConfig;

    // uni-directional many-to-one association to SequenceDbInstance
    @ManyToOne
    @JoinColumn(name = "seq_db_instance_id")
    private SequenceDbInstance sequenceDbInstance;

    // uni-directional many-to-one association to Taxon
    @ManyToOne
    @JoinColumn(name = "taxon_id", nullable = false)
    private Taxon taxon;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "seq_db_entry_object_tree_map", joinColumns = @JoinColumn(name = "seq_db_entry_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    public SequenceDbEntry() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getIdentifier() {
	return this.identifier;
    }

    public void setIdentifier(String identifier) {
	this.identifier = identifier;
    }

    public boolean getIsActive() {
	return isActive;
    }

    public void setIsActive(final boolean pIsActive) {
	isActive = pIsActive;
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

    public Long getRefFileBlockStart() {
	return this.refFileBlockStart;
    }

    public void setRefFileBlockStart(Long refFileBlockStart) {
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

    public void setTaxon(final Taxon taxon) {
	this.taxon = taxon;
    }

    void setObjectTreeIdByName(final Map<String, Long> objectTree) {
	objectTreeIdByName = objectTree;
    }

    public Map<String, Long> getObjectTreeIdByName() {
	return objectTreeIdByName;
    }

    public Long putObject(final String schemaName, final long objectId) {

	if (StringUtils.isEmpty(schemaName)) {
	    throw new IllegalArgumentException("Invalid schemaName");
	}

	Map<String, Long> localObjectTree = getObjectTreeIdByName();

	if (localObjectTree == null) {
	    localObjectTree = new HashMap<String, Long>();

	    setObjectTreeIdByName(localObjectTree);
	}

	return localObjectTree.put(schemaName, Long.valueOf(objectId));
    }

    public Long removeObject(final String schemaName) {
	Long result = null;

	final Map<String, Long> localObjectTree = getObjectTreeIdByName();
	if (localObjectTree != null) {
	    result = localObjectTree.remove(schemaName);
	}

	return result;
    }

}
