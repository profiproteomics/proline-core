package fr.proline.core.orm.msi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the peptide_set_peptide_instance_item database table.
 * 
 */
@Entity
@Table(name="peptide_instance_peptide_match_map")
public class PeptideInstancePeptideMatchMap implements Serializable {
	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private PeptideInstancePeptideMatchMapPK id;
  
	@ManyToOne
	@JoinColumn(name="result_summary_id")
	private ResultSummary resultSummary;
	
	@Column(name="serialized_properties")
	private String serializedProperties;
  
    //bi-directional many-to-one association to PeptideInstance
    @ManyToOne
  @JoinColumn(name="peptide_instance_id")
  @MapsId("peptideInstanceId")
  private PeptideInstance peptideInstance;
    
    //uni-directional many-to-one association to PeptideMatch
    @ManyToOne
  @JoinColumn(name="peptide_match_id")
  @MapsId("peptideMatchId")
  private PeptideMatch peptideMatch;

    public PeptideInstancePeptideMatchMap() {
    }

	public PeptideInstancePeptideMatchMapPK getId() {
		return this.id;
	}

	public void setId(PeptideInstancePeptideMatchMapPK id) {
		this.id = id;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public PeptideInstance getPeptideInstance() {
		return this.peptideInstance;
	}

	public void setPeptideInstance(PeptideInstance peptideInstance) {
		this.peptideInstance = peptideInstance;
	}
	
  public PeptideMatch getPeptideMatch() {
    return this.peptideMatch;
  }

  public void setPeptideMatch(PeptideMatch peptideMatch) {
    this.peptideMatch = peptideMatch;
  }
  
}