package fr.proline.core.orm.pdi;

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
 * The persistent class for the fasta_file_entry_index database table.
 * 
 */
@Entity
@Table(name = "fasta_file_entry_index")
public class FastaFileEntryIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "block_length")
    private int blockLength;

    @Column(name = "block_start")
    private long blockStart;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional many-to-one association to BioSequence
    @ManyToOne
    @JoinColumn(name = "bio_sequence_id")
    private BioSequence bioSequence;

    // uni-directional many-to-one association to SequenceDbEntry
    @ManyToOne
    @JoinColumn(name = "seq_db_entry_id")
    private SequenceDbEntry sequenceDbEntry;

    // uni-directional many-to-one association to SequenceDbInstance
    @ManyToOne
    @JoinColumn(name = "seq_db_instance_id")
    private SequenceDbInstance sequenceDbInstance;

    public FastaFileEntryIndex() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getBlockLength() {
	return blockLength;
    }

    public void setBlockLength(final int pBlockLength) {
	blockLength = pBlockLength;
    }

    public long getBlockStart() {
	return blockStart;
    }

    public void setBlockStart(final long pBlockStart) {
	blockStart = pBlockStart;
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
