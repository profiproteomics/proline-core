package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the bio_sequence_gene_map database table.
 * 
 */
@Entity
@Table(name = "seq_db_entry_protein_identifier_map")
public class DbEntryProteinIdentifierMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private DbEntryProteinIdentifierMapPK id;

	// uni-directional many-to-one association to SequenceDbEntry
	@ManyToOne
	@JoinColumn(name = "seq_db_entry_id")
	@MapsId("sequenceDbEntryId")
	private SequenceDbEntry dbEntry;

	// uni-directional many-to-one association to ProteinIdentifier
	@ManyToOne
	@JoinColumn(name = "protein_identifier_id")
	@MapsId("proteinIdentifierId")
	private ProteinIdentifier proteinIdentifier;

	// uni-directional many-to-one association to SequenceDbInstance
	@ManyToOne
	@JoinColumn(name = "seq_db_instance_id")
	private SequenceDbInstance dbInstance;

	public DbEntryProteinIdentifierMap() {
		this.id = new DbEntryProteinIdentifierMapPK();
	}

	public DbEntryProteinIdentifierMapPK getId() {
		return id;
	}

	public void setId(DbEntryProteinIdentifierMapPK id) {
		this.id = id;
	}

	public SequenceDbEntry getDbEntry() {
		return dbEntry;
	}

	public void setDbEntry(SequenceDbEntry dbEntry) {
		this.dbEntry = dbEntry;
	}

	public ProteinIdentifier getProteinIdentifier() {
		return proteinIdentifier;
	}

	public void setProteinIdentifier(ProteinIdentifier proteinIdentifier) {
		this.proteinIdentifier = proteinIdentifier;
	}

	public SequenceDbInstance getDbInstance() {
		return dbInstance;
	}

	public void setDbInstance(SequenceDbInstance dbInstance) {
		this.dbInstance = dbInstance;
	}

}