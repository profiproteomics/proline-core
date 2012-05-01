package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the seq_db_config database table.
 * 
 */
@Entity
@Table(name="seq_db_config")
public class SequenceDbConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private String type;

	private String alphabet;

	@Column(name="ref_entry_format")
	private String refEntryFormat;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//bi-directional many-to-one association to FastaParsingRule
    @ManyToOne
	@JoinColumn(name="fasta_parsing_rule_id")
	private FastaParsingRule fastaParsingRule;

    public SequenceDbConfig() {
    }

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getAlphabet() {
		return this.alphabet;
	}

	public void setAlphabet(String alphabet) {
		this.alphabet = alphabet;
	}

	public String getRefEntryFormat() {
		return this.refEntryFormat;
	}

	public void setRefEntryFormat(String refEntryFormat) {
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