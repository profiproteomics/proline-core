package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the protein_match_decoy_rule database table.
 * 
 */
@Entity
@Table(name="protein_match_decoy_rule")
public class ProteinMatchDecoyRule implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="ac_decoy_tag")
	private String acDecoyTag;

	private String name;

    public ProteinMatchDecoyRule() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getAcDecoyTag() {
		return this.acDecoyTag;
	}

	public void setAcDecoyTag(String acDecoyTag) {
		this.acDecoyTag = acDecoyTag;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

}