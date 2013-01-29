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
	@Column(name="peptide_id")
	private Integer peptideId;

	@Column(name="is_ok")
	private Boolean isOk;

    public PeptidePtmInsertStatus() {
    }

    public Integer getPeptideId() {
      return this.peptideId;
    }
  
    public void setPeptideId(Integer peptideId) {
        this.peptideId = peptideId;
    }

	public Boolean getIsOk() {
		return this.isOk;
	}

	public void setIsOk(Boolean isOk) {
		this.isOk = isOk;
	}



}