package fr.proline.core.orm.ps;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import static javax.persistence.CascadeType.PERSIST;


/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name="fr.proline.core.orm.ps.PtmSpecificity")
@Table(name="ptm_specificity")
public class PtmSpecificity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	@Column(name="id")
	private Integer id;

	private String location;

	private String residue;

	//bi-directional many-to-one association to Ptm
   @ManyToOne
	@JoinColumn(name="ptm_id")
	private Ptm ptm;

	//uni-directional many-to-one association to PtmClassification
   @ManyToOne(cascade = PERSIST)
	@JoinColumn(name="classification_id")
	private PtmClassification classification;

    @OneToMany(mappedBy="specificity", cascade = PERSIST)
    private List<PtmEvidence> evidences;
    
    public PtmSpecificity() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getResidue() {
		return this.residue;
	}

	public void setResidue(String residue) {
		this.residue = residue;
	}

	public Ptm getPtm() {
		return this.ptm;
	}

	public void setPtm(Ptm ptm) {
		this.ptm = ptm;
	}
	
	public PtmClassification getClassification() {
		return this.classification;
	}

	public void setClassification(PtmClassification classification) {
		this.classification = classification;
	}

	public List<PtmEvidence> getEvidences() {
		return evidences;
	}

	public void setEvidence(List<PtmEvidence> evidences) {
		this.evidences = evidences;
	}
	
	
	
}