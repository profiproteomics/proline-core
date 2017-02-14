package fr.proline.core.orm.pdi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the fasta_parsing_rule database table.
 * 
 */
@Entity
@Table(name = "fasta_parsing_rule")
public class FastaParsingRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "db_type")
    private String dbType;

    @Column(name = "entry_ac")
    private String entryAc;

    @Column(name = "entry_id")
    private String entryId;

    @Column(name = "entry_name")
    private String entryName;

    @Column(name = "gene_name")
    private String geneName;

    @Column(name = "organism_name")
    private String organismName;

    @Column(name = "taxon_id")
    private String taxonId;

    // bi-directional many-to-one association to SequenceDbConfig
    @OneToMany(mappedBy = "fastaParsingRule")
    private Set<SequenceDbConfig> sequenceDbConfigs;

    public FastaParsingRule() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getDbType() {
	return this.dbType;
    }

    public void setDbType(String dbType) {
	this.dbType = dbType;
    }

    public String getEntryAc() {
	return this.entryAc;
    }

    public void setEntryAc(String entryAc) {
	this.entryAc = entryAc;
    }

    public String getEntryId() {
	return this.entryId;
    }

    public void setEntryId(String entryId) {
	this.entryId = entryId;
    }

    public String getEntryName() {
	return this.entryName;
    }

    public void setEntryName(String entryName) {
	this.entryName = entryName;
    }

    public String getGeneName() {
	return this.geneName;
    }

    public void setGeneName(String geneName) {
	this.geneName = geneName;
    }

    public String getOrganismName() {
	return this.organismName;
    }

    public void setOrganismName(String organismName) {
	this.organismName = organismName;
    }

    public String getTaxonId() {
	return this.taxonId;
    }

    public void setTaxonId(String taxonId) {
	this.taxonId = taxonId;
    }

    public void setSequenceDbConfigs(final Set<SequenceDbConfig> seqDbConfigs) {
	sequenceDbConfigs = seqDbConfigs;
    }

    public Set<SequenceDbConfig> getSequenceDbConfigs() {
	return this.sequenceDbConfigs;
    }

    public void addSequenceDbConfig(final SequenceDbConfig seqDbConfig) {

	if (seqDbConfig != null) {
	    Set<SequenceDbConfig> seqDbConfigs = getSequenceDbConfigs();

	    if (seqDbConfigs == null) {
		seqDbConfigs = new HashSet<SequenceDbConfig>();

		setSequenceDbConfigs(seqDbConfigs);
	    }

	    seqDbConfigs.add(seqDbConfig);
	}

    }

    public void removeSequenceDbConfigs(final SequenceDbConfig seqDbConfig) {

	final Set<SequenceDbConfig> seqDbConfigs = getSequenceDbConfigs();
	if (seqDbConfigs != null) {
	    seqDbConfigs.remove(seqDbConfig);
	}

    }

}
