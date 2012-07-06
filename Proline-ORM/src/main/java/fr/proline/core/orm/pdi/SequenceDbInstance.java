package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import javax.persistence.*;

import java.sql.Timestamp;
import java.util.Date;


/**
 * The persistent class for the seq_db_instance database table.
 * 
 */
@Entity
@NamedQuery(name="findSeqDBByNameAndFile",
query="select seq from fr.proline.core.orm.pdi.SequenceDbInstance seq where seq.name = :name and seq.fastaFilePath = :filePath ")

@Table(name="seq_db_instance")
public class SequenceDbInstance implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="creation_timestamp")
	private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

	@Column(name="fasta_file_path")
	private String fastaFilePath;

	@Column(name="is_deleted")
	private Boolean isDeleted;

	@Column(name="is_indexed")
	private Boolean isIndexed;

	@Column(name="is_native")
	private Boolean isNative;

	private String name;

	@Column(name="ref_file_path")
	private String refFilePath;

	@Column(name="residue_count")
	private Integer residueCount;

	@Column(name="sequence_count")
	private Integer sequenceCount;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to SequenceDbRelease
   @ManyToOne(cascade = { PERSIST, REMOVE })
	@JoinColumn(name="seq_db_release_id")
	private SequenceDbRelease sequenceDbRelease;

    public SequenceDbInstance() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Timestamp getCreationTimestamp() {
		return this.creationTimestamp;
	}

	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public String getFastaFilePath() {
		return this.fastaFilePath;
	}

	public void setFastaFilePath(String fastaFilePath) {
		this.fastaFilePath = fastaFilePath;
	}

	public Boolean getIsDeleted() {
		return this.isDeleted;
	}

	public void setIsDeleted(Boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public Boolean getIsIndexed() {
		return this.isIndexed;
	}

	public void setIsIndexed(Boolean isIndexed) {
		this.isIndexed = isIndexed;
	}

	public Boolean getIsNative() {
		return this.isNative;
	}

	public void setIsNative(Boolean isNative) {
		this.isNative = isNative;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRefFilePath() {
		return this.refFilePath;
	}

	public void setRefFilePath(String refFilePath) {
		this.refFilePath = refFilePath;
	}

	public Integer getResidueCount() {
		return this.residueCount;
	}

	public void setResidueCount(Integer residueCount) {
		this.residueCount = residueCount;
	}

	public Integer getSequenceCount() {
		return this.sequenceCount;
	}

	public void setSequenceCount(Integer sequenceCount) {
		this.sequenceCount = sequenceCount;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public SequenceDbRelease getSequenceDbRelease() {
		return this.sequenceDbRelease;
	}

	public void setSequenceDbRelease(SequenceDbRelease sequenceDbRelease) {
		this.sequenceDbRelease = sequenceDbRelease;
	}
	
}