package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the fasta_file_entry_index database table.
 * 
 */
@Entity
@Table(name="fasta_file_entry_index")
public class FastaFileEntryIndex implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="block_length")
	private Integer blockLength;

	@Column(name="block_start")
	private Integer blockStart;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to BioSequence
    @ManyToOne
	@JoinColumn(name="bio_sequence_id")
	private BioSequence bioSequence;

	//uni-directional many-to-one association to SequenceDbEntry
    @ManyToOne
	@JoinColumn(name="seq_db_entry_id")
	private SequenceDbEntry sequenceDbEntry;

	//uni-directional many-to-one association to SequenceDbInstance
    @ManyToOne
	@JoinColumn(name="seq_db_instance_id")
	private SequenceDbInstance sequenceDbInstance;

    public FastaFileEntryIndex() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getBlockLength() {
		return this.blockLength;
	}

	public void setBlockLength(Integer blockLength) {
		this.blockLength = blockLength;
	}

	public Integer getBlockStart() {
		return this.blockStart;
	}

	public void setBlockStart(Integer blockStart) {
		this.blockStart = blockStart;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public BioSequence getBioSequence() {
		return this.bioSequence;
	}

	public void setBioSequence(BioSequence bioSequence) {
		this.bioSequence = bioSequence;
	}
	
	public SequenceDbEntry getSequenceDbEntry() {
		return this.sequenceDbEntry;
	}

	public void setSequenceDbEntry(SequenceDbEntry sequenceDbEntry) {
		this.sequenceDbEntry = sequenceDbEntry;
	}
	
	public SequenceDbInstance getSequenceDbInstance() {
		return this.sequenceDbInstance;
	}

	public void setSequenceDbInstance(SequenceDbInstance sequenceDbInstance) {
		this.sequenceDbInstance = sequenceDbInstance;
	}
	
}