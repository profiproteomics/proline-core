package fr.proline.core.orm.msi;


import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import fr.proline.core.orm.msi.dto.DMasterQuantProteinSet;
import fr.proline.core.orm.msi.dto.DQuantPeptideIon;
import fr.proline.core.orm.util.JsonSerializer;

/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name = "master_quant_peptide_ion")
public class MasterQuantPeptideIon implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private static final Logger LOG = LoggerFactory.getLogger(DMasterQuantProteinSet.class);
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "charge")
    private int charge;

    @Column(name = "moz")
    private double moz;

    @Column(name = "elution_time")
    private float elutionTime;

    @Column(name = "scan_number")
    private Integer scanNumber;
    
    @Column(name = "peptide_match_count")
    private int peptideMatchCount;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "lcms_master_feature_id")
    private Long lcmsMasterFeatureId;

    @Column(name = "master_quant_peptide_id")
    private long masterQuantPeptideId;

    @Column(name = "peptide_id")
    private Long peptideId;

    @ManyToOne
    @JoinColumn(name = "peptide_instance_id")
    private PeptideInstance peptideInstance;   

    @Column(name = "best_peptide_match_id")
    private Long bestPeptideMatchId;

    @Column(name = "unmodified_peptide_ion_id")
    private Long unmodifiedPeptideIonId;

    // Transient Variables not saved in database
    @Transient
    private Map<Long, DQuantPeptideIon> quantPeptideIonByQchIds = null;
    
    @ManyToOne
    @JoinColumn(name = "master_quant_component_id")
    private MasterQuantComponent masterQuantComponent;

    @OneToMany(mappedBy = "masterQuantPeptideIon")
    private Set<MasterQuantReporterIon> masterQuantReporterIons;

    @ManyToOne
    @JoinColumn(name = "result_summary_id")
    private ResultSummary resultSummary;


    
    public MasterQuantPeptideIon() {
    }
	
	public Map<Long, DQuantPeptideIon> getQuantPeptideIonByQchIds() {
		return quantPeptideIonByQchIds;
	}

	public void setQuantPeptideIonByQchIds(Map<Long, DQuantPeptideIon> quantPeptideIonByQchIds) {
		this.quantPeptideIonByQchIds = quantPeptideIonByQchIds;
	}
	
	public Map<Long, DQuantPeptideIon> parseQuantPeptideIonFromProperties(String quantPeptideIonData){

		try {
			List<DQuantPeptideIon> quantPepIons = JsonSerializer.getMapper().readValue(quantPeptideIonData, new TypeReference<List<DQuantPeptideIon>>() {});
			
			quantPeptideIonByQchIds = new HashMap<Long, DQuantPeptideIon>();		
			if (quantPepIons != null) {
				for(int i=0;i<quantPepIons.size();i++){
					DQuantPeptideIon nextQuantPepIon = quantPepIons.get(i);
					if(nextQuantPepIon != null) {
						quantPeptideIonByQchIds.put(nextQuantPepIon.getQuantChannelId(),nextQuantPepIon);
					}
				}
			}
		 
					
		}catch(Exception e) {
			LOG.warn("Error Parsing DQuantPeptideIon ",e);
			LOG.warn("quantPeptideIonData= "+quantPeptideIonData);
			quantPeptideIonByQchIds = null;
		}
		
		return quantPeptideIonByQchIds;
	}
	
    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getCharge() {
	return charge;
    }

    public void setCharge(int charge) {
	this.charge = charge;
    }

    public double getMoz() {
	return moz;
    }

    public void setMoz(final double pMoz) {
	moz = pMoz;
    }

    public float getElutionTime() {
	return elutionTime;
    }

    public void setElutionTime(final float pElutionTime) {
	elutionTime = pElutionTime;
    }

    public Integer getScanNumber() {
	return scanNumber;
    }

    public void setScanNumber(Integer scanNumber) {
	this.scanNumber = scanNumber;
    }
    
    public int getPeptideMatchCount() {
  return peptideMatchCount;
    }

    public void setPeptideMatchCount(final int pPeptideMatchCount) {
  peptideMatchCount = pPeptideMatchCount;
    }

    public String getSerializedProperties() {
	return serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public Long getLcmsMasterFeatureId() {
	return lcmsMasterFeatureId;
    }

    public void setLcmsMasterFeatureId(final Long pLcmsMasterFeatureId) {
	lcmsMasterFeatureId = pLcmsMasterFeatureId;
    }

    public long getMasterQuantPeptideId() {
	return masterQuantPeptideId;
    }

    public void setMasterQuantPeptideId(final long pMasterQuantPeptideId) {
	masterQuantPeptideId = pMasterQuantPeptideId;
    }

    public Long getPeptideId() {
	return peptideId;
    }

    public void setPeptideId(final Long pPeptideId) {
	peptideId = pPeptideId;
    }

    public PeptideInstance getPeptideInstance() {
	return peptideInstance;
    }

    public void setPeptideInstance(final PeptideInstance pPeptideInstance) {
	peptideInstance = pPeptideInstance;
    }

    public Long getBestPeptideMatchId() {
	return bestPeptideMatchId;
    }

    public void setBestPeptideMatchId(final Long pBestPeptideMatchId) {
	bestPeptideMatchId = pBestPeptideMatchId;
    }

    public Long getUnmodifiedPeptideIonId() {
	return unmodifiedPeptideIonId;
    }

    public void setUnmodifiedPeptideIonId(final Long pUnmodifiedPeptideIonId) {
	unmodifiedPeptideIonId = pUnmodifiedPeptideIonId;
    }

    public MasterQuantComponent getMasterQuantComponent() {
	return masterQuantComponent;
    }

    public void setMasterQuantComponent(MasterQuantComponent masterQuantComponent) {
	this.masterQuantComponent = masterQuantComponent;
    }

    public ResultSummary getResultSummary() {
	return resultSummary;
    }

    public void setResultSummary(ResultSummary resultSummary) {
	this.resultSummary = resultSummary;
    }

    public Set<MasterQuantReporterIon> getMasterQuantReporterIons() {
	return masterQuantReporterIons;
    }

    public void setMasterQuantReporterIons(Set<MasterQuantReporterIon> masterQuantReporterIons) {
	this.masterQuantReporterIons = masterQuantReporterIons;
    }

}
