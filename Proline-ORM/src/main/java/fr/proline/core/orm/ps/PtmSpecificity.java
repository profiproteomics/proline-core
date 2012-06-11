package fr.proline.core.orm.ps;

import static javax.persistence.CascadeType.PERSIST;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;


/**
 * The persistent class for the ptm_specificity database table.
 * 
 */
@Entity(name="fr.proline.core.orm.ps.PtmSpecificity")
@NamedQueries({
@NamedQuery(name="findPtmSpecificityByNameLocResidue",
query="select ps from fr.proline.core.orm.ps.PtmSpecificity ps where ps.residue=:residu and ps.location=:location and ps.ptm.shortName=:ptmName"),

@NamedQuery(name="findPtmSpecificityNoResiduByNameLoc",
query="select ps from fr.proline.core.orm.ps.PtmSpecificity ps where ps.residue is null and ps.location = :location and ps.ptm.shortName = :ptmName")

})

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

	public boolean addEvidence(PtmEvidence evidence) {
		if (this.evidences == null)
			this.evidences = new ArrayList<PtmEvidence>();
		return this.evidences.add(evidence);
	}
	
	public List<PtmEvidence> getEvidences() {
		return evidences;
	}


}