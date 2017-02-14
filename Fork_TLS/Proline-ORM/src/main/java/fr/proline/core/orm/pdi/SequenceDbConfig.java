package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the seq_db_config database table.
 * 
 */
@Entity
@NamedQuery(name = "findSequenceDbConfigForName", query = "select sdc from fr.proline.core.orm.pdi.SequenceDbConfig sdc"
	+ " where upper(sdc.name) = :name")
@Table(name = "seq_db_config")
public class SequenceDbConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Format {
	SWISS, GENEBANK, GFF
    };

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String name;

    @Column(name = "is_native")
    private boolean isNative;

    @Enumerated(EnumType.STRING)
    private Alphabet alphabet;

    @Column(name = "ref_entry_format")
    @Enumerated(EnumType.STRING)
    private Format refEntryFormat;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional many-to-one association to FastaParsingRule
    @ManyToOne(cascade = { PERSIST })
    @JoinColumn(name = "fasta_parsing_rule_id")
    private FastaParsingRule fastaParsingRule;

    // bi-directional many-to-one association to SequenceDbInstance
    @OneToMany(mappedBy = "sequenceDbConfig")
    private Set<SequenceDbInstance> sequenceDbInstances;

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public Alphabet getAlphabet() {
	return this.alphabet;
    }

    public void setAlphabet(Alphabet alphabet) {
	this.alphabet = alphabet;
    }

    public Format getRefEntryFormat() {
	return this.refEntryFormat;
    }

    public void setRefEntryFormat(Format refEntryFormat) {
	this.refEntryFormat = refEntryFormat;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public FastaParsingRule getFastaParsingRule() {
	return this.fastaParsingRule;
    }

    public void setFastaParsingRule(FastaParsingRule fastaParsingRule) {
	this.fastaParsingRule = fastaParsingRule;
    }

    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public boolean getIsNative() {
	return isNative;
    }

    public void setIsNative(final boolean pIsNative) {
	isNative = pIsNative;
    }

    public void setSequenceDbInstances(final Set<SequenceDbInstance> seqDbInstances) {
	sequenceDbInstances = seqDbInstances;
    }

    public Set<SequenceDbInstance> getSequenceDbInstances() {
	return sequenceDbInstances;
    }

    public void addSequenceDbInstance(final SequenceDbInstance seqDbInstance) {

	if (seqDbInstance != null) {
	    Set<SequenceDbInstance> seqDbInstances = getSequenceDbInstances();

	    if (seqDbInstances == null) {
		seqDbInstances = new HashSet<SequenceDbInstance>();

		setSequenceDbInstances(seqDbInstances);
	    }

	    seqDbInstances.add(seqDbInstance);
	}

    }

    public void removeSequenceDbInstance(final SequenceDbInstance seqDbInstance) {

	final Set<SequenceDbInstance> seqDbInstances = getSequenceDbInstances();
	if (seqDbInstances != null) {
	    seqDbInstances.remove(seqDbInstance);
	}

    }

}
