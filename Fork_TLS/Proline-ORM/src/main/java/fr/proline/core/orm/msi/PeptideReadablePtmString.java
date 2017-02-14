package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@NamedQuery(name = "findPeptideReadablePtmStrForPepAndRS", query = "select prps from fr.proline.core.orm.msi.PeptideReadablePtmString prps"
	+ " where (prps.peptide.id = :peptideId) and (prps.resultSet.id = :resultSetId)")
@Table(name = "peptide_readable_ptm_string")
public class PeptideReadablePtmString implements Serializable {

    private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PeptideReadablePtmStringPK id;

    @Column(name = "readable_ptm_string")
    private String readablePtmString;

    @ManyToOne
    @JoinColumn(name = "peptide_id")
    @MapsId("peptideId")
    private Peptide peptide;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    @MapsId("resultSetId")
    private ResultSet resultSet;

    public PeptideReadablePtmString() {
    }

	public PeptideReadablePtmStringPK getId() {
		return this.id;
	}

	public void setId(PeptideReadablePtmStringPK id) {
		this.id = id;
	}

    public void setReadablePtmString(final String pReadablePtmString) {
	readablePtmString = pReadablePtmString;
    }

    public String getReadablePtmString() {
	return readablePtmString;
    }

    public void setPeptide(final Peptide pPeptide) {
	peptide = pPeptide;
    }

    public Peptide getPeptide() {
	return peptide;
    }

    public void setResultSet(final ResultSet pResultSet) {
	resultSet = pResultSet;
    }

    public ResultSet getResultSet() {
	return resultSet;
    }

}
