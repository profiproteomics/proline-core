package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * The persistent class for the seq_db_instance database table.
 * 
 */
@Entity
@NamedQuery(name = "findSeqDBByNameAndFile", query = "select seq from fr.proline.core.orm.pdi.SequenceDbInstance seq where seq.sequenceDbConfig.name = :name and seq.fastaFilePath = :filePath ")
@Table(name = "seq_db_instance")
public class SequenceDbInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "creation_timestamp")
    private Timestamp creationTimestamp = new Timestamp(new Date().getTime());

    @Column(name = "fasta_file_path")
    private String fastaFilePath;

    @Column(name = "is_deleted")
    private boolean isDeleted;

    @Column(name = "is_indexed")
    private boolean isIndexed;

    @Column(name = "revision")
    private int revision;

    @Column(name = "ref_file_path")
    private String refFilePath;

    @Column(name = "residue_count")
    private Integer residueCount;

    @Column(name = "sequence_count")
    private int sequenceCount;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // uni-directional one-to-one association to SequenceDbRelease
    @OneToOne(cascade = { PERSIST, REMOVE })
    @JoinColumn(name = "seq_db_release_id")
    private SequenceDbRelease sequenceDbRelease;

    // bi-directional many-to-one association to SequenceDbConfig
    @ManyToOne
    @JoinColumn(name = "seq_db_config_id")
    private SequenceDbConfig sequenceDbConfig;

    public SequenceDbInstance() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public Timestamp getCreationTimestamp() {
	Timestamp result = null;

	if (creationTimestamp != null) { // Should not be null
	    result = (Timestamp) creationTimestamp.clone();
	}

	return result;
    }

    public void setCreationTimestamp(final Timestamp pCreationTimestamp) {

	if (pCreationTimestamp == null) {
	    throw new IllegalArgumentException("PCreationTimestamp is null");
	}

	creationTimestamp = (Timestamp) pCreationTimestamp.clone();
    }

    public String getFastaFilePath() {
	return this.fastaFilePath;
    }

    public void setFastaFilePath(String fastaFilePath) {
	this.fastaFilePath = fastaFilePath;
    }

    public boolean getIsDeleted() {
	return isDeleted;
    }

    public void setIsDeleted(final boolean pIsDeleted) {
	isDeleted = pIsDeleted;
    }

    public boolean getIsIndexed() {
	return isIndexed;
    }

    public void setIsIndexed(final boolean pIsIndexed) {
	isIndexed = pIsIndexed;
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

    public int getSequenceCount() {
	return this.sequenceCount;
    }

    public void setSequenceCount(int sequenceCount) {
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

    public SequenceDbConfig getSequenceDbConfig() {
	return sequenceDbConfig;
    }

    public void setSequenceDbConfig(SequenceDbConfig sequenceDbConfig) {
	this.sequenceDbConfig = sequenceDbConfig;
    }

    public int getRevision() {
	return revision;
    }

    public void setRevision(final int pRevision) {
	revision = pRevision;
    }

}
