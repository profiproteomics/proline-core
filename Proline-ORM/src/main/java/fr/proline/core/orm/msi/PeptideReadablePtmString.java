package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@NamedQuery(name = "findPeptideReadablePtmStrForPepAndRS", query = "select prps from fr.proline.core.orm.msi.PeptideReadablePtmString prps"
	+ " where (prps.peptide.id = :peptideId) and (prps.resultSet.id = :resultSetId)")
@Table(name = "peptide_readable_ptm_string")
public class PeptideReadablePtmString implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "readable_ptm_string")
    private String readablePtmString;

    @ManyToOne
    @JoinColumn(name = "peptide_id")
    private Peptide peptide;

    @ManyToOne
    @JoinColumn(name = "result_set_id")
    private ResultSet resultSet;

    public void setId(final long pId) {
	id = pId;
    }

    public long getId() {
	return id;
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
