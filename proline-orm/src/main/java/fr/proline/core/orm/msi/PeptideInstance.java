package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

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
import javax.persistence.Transient;

import fr.proline.core.orm.msi.dto.*;

/**
 * The persistent class for the peptide_instance database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findPepInstByPepMatch", query = "select pi from PeptideInstance pi, IN (pi.peptideInstancePeptideMatchMaps) pm where pm.id.peptideMatchId = :pmID "),

	@NamedQuery(name = "findPepInstForPeptideId", query = "select pi from PeptideInstance pi where pi.peptide.id = :pepID ")

})
@Table(name = "peptide_instance")
public class PeptideInstance implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "best_peptide_match_id")
	private long bestPeptideMatchId;

	@Column(name = "master_quant_component_id")
	private Long masterQuantComponentId;

	@Column(name = "peptide_match_count")
	private int peptideMatchCount;

	@Column(name = "protein_match_count")
	private int proteinMatchCount;

	@Column(name = "protein_set_count")
	private int proteinSetCount;

	@Column(name = "validated_protein_set_count")
	private int validatedProteinSetCount;

	@Column(name = "total_leaves_match_count")
	private int totalLeavesMatchCount;

	@Column(name = "elution_time")
	private Float elutionTime;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	@ManyToOne
	@JoinColumn(name = "peptide_id")
	private Peptide peptide;

	@Column(name = "selection_level")
	private int selectionLevel;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "unmodified_peptide_id")
	private Long unmodifiedPeptideId;

	// bi-directional many-to-one association to PeptideSetPeptideInstanceItem
	@OneToMany(mappedBy = "peptideInstance")
	private Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems;

	// uni-directional many-to-one association to PeptideInstancePeptideMatchMap
	@OneToMany(mappedBy = "peptideInstance")
	private Set<PeptideInstancePeptideMatchMap> peptideInstancePeptideMatchMaps;

	/*
	 * @OneToMany
	 * 
	 * @JoinTable( name="peptide_instance_peptide_match_map" , joinColumns={
	 * 
	 * @JoinColumn(name="peptide_match_id") } , inverseJoinColumns={
	 * 
	 * @JoinColumn(name="peptide_instance_id") } ) private Set<PeptideMatch> peptidesMatches;
	 * 
	 * public PeptideInstance() { }
	 */

	// Transient Variables not saved in database
	@Transient
	private TransientData transientData = null;

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public long getBestPeptideMatchId() {
		return bestPeptideMatchId;
	}

	public void setBestPeptideMatchId(final long pBestPeptideMatchId) {
		bestPeptideMatchId = pBestPeptideMatchId;
	}

	public Long getMasterQuantComponentId() {
		return masterQuantComponentId;
	}

	public void setMasterQuantComponentId(final Long pMasterQuantComponentId) {
		masterQuantComponentId = pMasterQuantComponentId;
	}

	public Peptide getPeptide() {
		return peptide;
	}

	public void setPeptide(final Peptide pPeptide) {
		peptide = pPeptide;
	}

	public int getPeptideMatchCount() {
		return peptideMatchCount;
	}

	public void setPeptideMatchCount(final int pPeptideMatchCount) {
		peptideMatchCount = pPeptideMatchCount;
	}

	public int getProteinMatchCount() {
		return proteinMatchCount;
	}

	public void setProteinMatchCount(final int pProteinMatchCount) {
		proteinMatchCount = pProteinMatchCount;
	}

	public int getProteinSetCount() {
		return proteinSetCount;
	}

	public void setProteinSetCount(final int pProteinSetCount) {
		proteinSetCount = pProteinSetCount;
	}

	public int getTotalLeavesMatchCount() {
		return totalLeavesMatchCount;
	}

	public void setTotalLeavesMatchCount(final int pTotalLeavesMatchCount) {
		totalLeavesMatchCount = pTotalLeavesMatchCount;
	}

	public ResultSummary getResultSummary() {
		return this.resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public int getSelectionLevel() {
		return selectionLevel;
	}

	public void setSelectionLevel(final int pSelectionLevel) {
		selectionLevel = pSelectionLevel;
	}

	public Float getElutionTime() {
		return elutionTime;
	}

	public void setElutionTime(Float elutionTime) {
		this.elutionTime = elutionTime;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Long getUnmodifiedPeptideId() {
		return unmodifiedPeptideId;
	}

	public void setUnmodifiedPeptideId(final Long pUnmodifiedPeptideId) {
		unmodifiedPeptideId = pUnmodifiedPeptideId;
	}

	public int getValidatedProteinSetCount() {
		return validatedProteinSetCount;
	}

	public void setValidatedProteinSetCount(final int pValidatedProteinSetCount) {
		validatedProteinSetCount = pValidatedProteinSetCount;
	}

	public Set<PeptideSetPeptideInstanceItem> getPeptideSetPeptideInstanceItems() {
		return this.peptideSetPeptideInstanceItems;
	}

	public void setPeptideSetPeptideInstanceItems(
		Set<PeptideSetPeptideInstanceItem> peptideSetPeptideInstanceItems) {
		this.peptideSetPeptideInstanceItems = peptideSetPeptideInstanceItems;
	}

	public Set<PeptideInstancePeptideMatchMap> getPeptideInstancePeptideMatchMaps() {
		return this.peptideInstancePeptideMatchMaps;
	}

	public void setPeptideInstancePeptideMatchMaps(
		Set<PeptideInstancePeptideMatchMap> peptideInstancePeptideMatchMaps) {
		this.peptideInstancePeptideMatchMaps = peptideInstancePeptideMatchMaps;
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

		private DPeptideMatch bestPeptideMatch = null;

		private DProteinSet[] proteinSetArray = null;

		private long[] peptideMatchesId;
		private DPeptideMatch[] peptideMatches;

		protected TransientData() {
		}

		public DProteinSet[] getProteinSetArray() {
			return proteinSetArray;
		}

		public void setProteinSetArray(DProteinSet[] proteinSetArray) {
			this.proteinSetArray = proteinSetArray;
		}

		public DPeptideMatch getBestPeptideMatch() {
			return bestPeptideMatch;
		}

		public void setBestPeptideMatch(DPeptideMatch bestPeptideMatch) {
			this.bestPeptideMatch = bestPeptideMatch;
		}

		public DPeptideMatch[] getPeptideMatches() {
			return peptideMatches;
		}

		public void setPeptideMatches(DPeptideMatch[] peptideMatches) {
			this.peptideMatches = peptideMatches;
		}

		public long[] getPeptideMatchesId() {
			return peptideMatchesId;
		}

		public void setPeptideMatchesId(long[] peptideMatchesId) {
			this.peptideMatchesId = peptideMatchesId;
		}

	}

}
