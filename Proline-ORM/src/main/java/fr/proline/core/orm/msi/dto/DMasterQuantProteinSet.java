package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import fr.proline.core.orm.util.JsonSerializer;

/**
 * Infer MasterQuantProteinSet representation from MasterQuantComponent table
 * for those linked to proteinSet. 
 * ObjectTree Schema = "object_tree.quant_protein_sets"
 *
 */
public class DMasterQuantProteinSet {
	  private static final Logger LOG = LoggerFactory.getLogger(DMasterQuantProteinSet.class);
  
	/* MasterQuantComponent fields */ 
	private long m_id ; //id of associated MasterQuantComponent
	
	private long m_proteinSetId;
  
    private int m_selectionLevel; 
    	
	private long m_objectTreeId;
	
	private String m_serializedProperties;
    
	private Long m_quantResultSummaryId;
	
	//DProteinSet to provide access to DProteinMatch
	private DProteinSet m_dProteinSet;
		
	// serializedProperties as a map
	private MasterQuantProteinSetProperties mqProteinSetProperties;
	
	//List of QuantProteinSet ... to be loaded before use 
	Map<Long, DQuantProteinSet> quantProteinSetByQchIds = new HashMap<Long, DQuantProteinSet>();
	
	
	public DMasterQuantProteinSet() {
		
	}

	public DMasterQuantProteinSet(long id, int selectionLevel,
			Long objectTreeId, String serializedProperties, Long quantResultSummaryId, long proteinSetId) {
		super();
		this.m_id = id;
		this.m_quantResultSummaryId = quantResultSummaryId;
		this.m_selectionLevel = selectionLevel;
		this.m_serializedProperties = serializedProperties;
		this.m_objectTreeId = objectTreeId;
		this.m_proteinSetId = proteinSetId;
		
	}

	public long getId() {
		return m_id;
	}

	public void setId(long id) {
		this.m_id = id;
	}

	public Long getQuantResultSummaryId() {
		return m_quantResultSummaryId;
	}

	public void setQuantResultSummaryId(Long quantResultSummaryId) {
		this.m_quantResultSummaryId = quantResultSummaryId;
	}

	public int getSelectionLevel() {
		return m_selectionLevel;
	}


	public void setSelectionLevel(int selectionLevel) {
		this.m_selectionLevel = selectionLevel;
	}
	
	public Map<Long, DQuantProteinSet> parseQuantProteinSetFromProperties(String quantProtSetdata){

		try {
			List<DQuantProteinSet> quantProtSets = JsonSerializer.getMapper().readValue(quantProtSetdata, new TypeReference<List<DQuantProteinSet>>() {});
			
			quantProteinSetByQchIds = new HashMap<Long, DQuantProteinSet>();		
			for(int i=0;i<quantProtSets.size();i++){
				DQuantProteinSet nextQuantProtSet = quantProtSets.get(i);
				if (nextQuantProtSet != null) {
					quantProteinSetByQchIds.put(nextQuantProtSet.quantChannelId,nextQuantProtSet);
				}
			}
		 
					
		}catch(Exception e) {
			LOG.warn("Error Parsing DQuantProteinSet ",e);
			quantProteinSetByQchIds = null;
		}
		
		return quantProteinSetByQchIds;
	}
	
	
	public Map<Long, DQuantProteinSet> getQuantProteinSetByQchIds() {
		return quantProteinSetByQchIds;
	}

	public void setQuantProteinSetByQchIds(Map<Long, DQuantProteinSet> quantProteinSetByQchIds) {
		this.quantProteinSetByQchIds = quantProteinSetByQchIds;
	}

	public long getObjectTreeId() {
		return m_objectTreeId;
	}


	public void setObjectTreeId(long objectTreeId) {
		this.m_objectTreeId = objectTreeId;
	}


	public long getProteinSetId() {
		return m_proteinSetId;
	}



	public void setProteinSetId(long m_proteinSetId) {
		this.m_proteinSetId = m_proteinSetId;
	}

	

	public String getSerializedProperties() {
		return m_serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.m_serializedProperties = serializedProperties;
		this.mqProteinSetProperties = null; //reinit map
	}


	public void setMasterQuantProtSetProperties(MasterQuantProteinSetProperties properties) {
		this.mqProteinSetProperties = properties;
	}
	
    public MasterQuantProteinSetProperties getMasterQuantProtSetProperties() throws Exception {
    	if ((mqProteinSetProperties == null) && (m_serializedProperties != null)) {
    		try {
    			//	Parse properties to get Values
    			mqProteinSetProperties = parseJsonProperties(m_serializedProperties);
    		} catch(Exception e){
    			LOG.warn(" Error parsiong MasterQuantProteinSetProperties ",e);
    			
    		}
    		
    	}
    	return mqProteinSetProperties;
    }


	private MasterQuantProteinSetProperties parseJsonProperties(String jsonProperties) throws Exception {
		
		MasterQuantProteinSetProperties mqProtSetProperties = JsonSerializer.getMapper().readValue(jsonProperties,  MasterQuantProteinSetProperties.class);
//		LOG.debug("selected_master_quant_peptide_ions_ids readed  _"+mqProperties.selectedMasterQuantPeptideIonIds+"_ ");		
//		LOG.debug("selectedMasterQuantPeptideIds readed  _"+mqProperties.selectedMasterQuantPeptideIds+"_ ");
//		LOG.debug("MasterQuantProteinSetProfile readed  _"+mqProperties.getMqProtSetProfilesByGroupSetupNumber()+"_ ");
			
		return mqProtSetProperties;
	}

	public DProteinSet getProteinSet() {
		return m_dProteinSet;
	}

	public void setProteinSet(DProteinSet proteinSet) {
		this.m_dProteinSet = proteinSet;
	}


}