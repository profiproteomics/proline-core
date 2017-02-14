package fr.proline.core.orm.ps;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the peptide_ptm_insert_status database table.
 * 
 */
@Entity
@Table(name = "peptide_ptm_insert_status")
public class PeptidePtmInsertStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "peptide_id")
    private long peptideId;

    @Column(name = "is_ok")
    private boolean isOk;

    public PeptidePtmInsertStatus() {
    }

    public long getPeptideId() {
	return peptideId;
    }

    public void setPeptideId(final long pPeptideId) {
	peptideId = pPeptideId;
    }

    public boolean getIsOk() {
	return isOk;
    }

    public void setIsOk(final boolean pIsOk) {
	isOk = pIsOk;
    }

}
