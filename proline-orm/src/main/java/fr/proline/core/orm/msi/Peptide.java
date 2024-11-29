package fr.proline.core.orm.msi;

import fr.proline.core.orm.msi.dto.DPeptidePTM;
import fr.proline.core.orm.msi.dto.DProteinSet;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * The persistent class for the peptide database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.Peptide")
@NamedQueries({
	@NamedQuery(name = "findMsiPepsForSeq", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where upper(p.sequence) = :seq"),

	@NamedQuery(name = "findMsiPepsForIds", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where p.id in :ids"),

	@NamedQuery(name = "findMsiPeptForSeqAndPtmStr", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where (upper(p.sequence) = :seq) and (upper(p.ptmString) = :ptmStr)"),

	@NamedQuery(name = "findMsiPepsForSeqWOPtm", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where (upper(p.sequence) = :seq) and (p.ptmString is null)")

})
public class Peptide implements Serializable, Comparable<Peptide> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "calculated_mass")
	private double calculatedMass;

	@Column(name = "ptm_string")
	private String ptmString;

	private String sequence;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	// uni-directional many-to-one association to AtomLabel
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "atom_label_id")
	private AtomLabel atomLabel;

	// bi-directional many-to-one association to PeptidePtm
	@OneToMany(mappedBy = "peptide")
	private Set<PeptidePtm> ptms;

	// Transient Variables not saved in database
	@Transient
	private TransientData transientData = null;

	public Peptide() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public double getCalculatedMass() {
		return this.calculatedMass;
	}

	public void setCalculatedMass(double calculatedMass) {
		this.calculatedMass = calculatedMass;
	}

	public String getPtmString() {
		return this.ptmString;
	}

	public void setPtmString(String ptmString) {
		this.ptmString = ptmString;
	}

	public String getSequence() {
		return this.sequence;
	}

	public void setSequence(String sequence) {
		this.sequence = sequence;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public AtomLabel getAtomLabel() {
		return this.atomLabel;
	}

	public void setAtomLabel(AtomLabel atomLabel) {
		this.atomLabel = atomLabel;
	}

	public Set<PeptidePtm> getPtms() {
		return this.ptms;
	}

	public void setPtms(Set<PeptidePtm> ptms) {
		this.ptms = ptms;
	}

	public TransientData getTransientData() {
		if (transientData == null) {
			transientData = new TransientData();
		}
		return transientData;
	}

	/**
	 * Transient Data which will be not saved in database Used by the Proline Studio IHM
	 * 
	 * @author JM235353
	 */
	public static class TransientData implements Serializable {
		private static final long serialVersionUID = 1L;

		private ArrayList<DProteinSet> proteinSetArray = null;

		private HashMap<Integer, DPeptidePTM> dpeptidePtmMap = null;

		private PeptideReadablePtmString peptideReadablePtmString = null;
		private boolean peptideReadablePtmStringLoaded = false;

		protected TransientData() {
		}

		public HashMap<Integer, DPeptidePTM> getDPeptidePtmMap() {
			return dpeptidePtmMap;
		}

		public void setDPeptidePtmMap(HashMap<Integer, DPeptidePTM> peptidePtmMap) {
			dpeptidePtmMap = peptidePtmMap;
		}


		public PeptideReadablePtmString getPeptideReadablePtmString() {
			return peptideReadablePtmString;
		}

		public void setPeptideReadablePtmString(PeptideReadablePtmString peptideReadablePtmString) {
			this.peptideReadablePtmString = peptideReadablePtmString;
		}

		public void setPeptideReadablePtmStringLoaded() {
			peptideReadablePtmStringLoaded = true;
		}

		public boolean isPeptideReadablePtmStringLoaded() {
			return peptideReadablePtmStringLoaded;
		}

		public ArrayList<DProteinSet> getProteinSetArray() {
			return proteinSetArray;
		}

		public void setProteinSetArray(ArrayList<DProteinSet> proteinSetArray) {
			this.proteinSetArray = proteinSetArray;
		}

	}

	/**
	 * Method for Comparable interface. Compare Peptides according to their sequence
	 * 
	 * @param p
	 * @return
	 */
	@Override
	public int compareTo(Peptide p) {
		return sequence.compareTo(p.sequence);
	}

}
