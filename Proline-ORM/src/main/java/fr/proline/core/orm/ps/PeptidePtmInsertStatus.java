package fr.proline.core.orm.ps;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peptide_ptm_insert_status database table.
 * 
 */
@Entity
@Table(name="peptide_ptm_insert_status")
public class PeptidePtmInsertStatus implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	@Column(name="is_ok")
	private Boolean isOk;

	@Column(name="peptide_id")
	private Integer peptideId;

    public PeptidePtmInsertStatus() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getIsOk() {
		return this.isOk;
	}

	public void setIsOk(Boolean isOk) {
		this.isOk = isOk;
	}

	public Integer getPeptideId() {
		return this.peptideId;
	}

	public void setPeptideId(Integer peptideId) {
		this.peptideId = peptideId;
	}

}