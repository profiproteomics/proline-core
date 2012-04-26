package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the protein_match_seq_database_map database table.
 * 
 */
@Entity
@Table(name="protein_match_seq_database_map")
public class ProteinMatchSeqDatabaseMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private ProteinMatchSeqDatabaseMapPK id;

	@ManyToOne
	@JoinColumn(name="result_set_id")
	private ResultSet resultSet;

    public ProteinMatchSeqDatabaseMap() {
    }

	public ProteinMatchSeqDatabaseMapPK getId() {
		return this.id;
	}

	public void setId(ProteinMatchSeqDatabaseMapPK id) {
		this.id = id;
	}
	
	public ResultSet getResultSet() {
		return this.resultSet;
	}

	public void setResultSetId(ResultSet resultSet) {
		this.resultSet = resultSet;
	}

}