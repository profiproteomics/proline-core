package fr.proline.core.orm.pdi;

import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;

import java.io.Serializable;
import javax.persistence.*;

/**
 * The persistent class for the seq_db_config database table.
 * 
 */
@Entity
@Table(name = "seq_db_config")
public class SequenceDbConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum Alphabet { AA, DNA };
	
	public enum Format { SWISS, GENEBANK, GFF };

	public enum Type { IPI, SPROT, TREMBL, NCBI }
	
	@Id
	@Enumerated(EnumType.STRING)
	private Type type;

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

	protected SequenceDbConfig() {

	}
	
	public SequenceDbConfig(Type type) {
		setType(type);
	}

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
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

}