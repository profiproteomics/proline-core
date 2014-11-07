package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		
		quantProteinSetByQchIds = new HashMap<Long, DQuantProteinSet>();
		try {
			List quantProtSetsJson =  JsonSerializer.getMapper().readValue(quantProtSetdata, List.class);
			for(int i=0;i<quantProtSetsJson.size();i++){
				Map<String,Object> quantProtSetValues = (Map<String,Object>)quantProtSetsJson.get(i); 
				
				DQuantProteinSet nextQuantProtSet = new DQuantProteinSet((Float)quantProtSetValues.get("raw_abundance"), (Float)quantProtSetValues.get("abundance"),
						(Integer)quantProtSetValues.get("selection_level"),(Integer)quantProtSetValues.get("peptide_matches_count"),  (Long)quantProtSetValues.get("quant_channel_id"));
				if(quantProtSetValues.containsKey("protein_set_id"))
					nextQuantProtSet.setProteinSetId((Long)quantProtSetValues.get("protein_set_id"));
				if(quantProtSetValues.containsKey("protein_set_id"))
					nextQuantProtSet.setProteinMatchId((Long)quantProtSetValues.get("protein_match_id"));
				
				quantProteinSetByQchIds.put((Long)quantProtSetValues.get("quant_channel_id"),nextQuantProtSet);
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
	
	@SuppressWarnings("unchecked")
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


//TODO ! Use better jackson !!!
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private MasterQuantProteinSetProperties parseJsonProperties(String jsonProperties) throws Exception {
		
		MasterQuantProteinSetProperties mqProtSetProperties = JsonSerializer.getMapper().readValue(jsonProperties,  MasterQuantProteinSetProperties.class);
//		LOG.debug("selected_master_quant_peptide_ions_ids readed  _"+mqProperties.selectedMasterQuantPeptideIonIds+"_ ");		
//		LOG.debug("selectedMasterQuantPeptideIds readed  _"+mqProperties.selectedMasterQuantPeptideIds+"_ ");
//		LOG.debug("MasterQuantProteinSetProfile readed  _"+mqProperties.getMqProtSetProfilesByGroupSetupNumber()+"_ ");
		
//		
//		Map<String,Object> root =  JsonSerializer.getMapper().readValue(jsonProperties, Map.class);
//		
//		Map profileByGrpSetupJson = (Map) root.get("mq_prot_set_profiles_by_group_setup_number");
//		List selectedMQPepIds =  (List) root.get("selected_master_quant_peptide_ids");
//		List selectedMQPepIonIds =  (List) root.get("selected_master_quant_peptide_ions_ids");
//		
//		
//		MasterQuantProteinSetProperties mqProtSetProperties = new MasterQuantProteinSetProperties();
//		mqProtSetProperties.setSelectedMasterQuantPeptideIonIds(selectedMQPepIonIds);
//		mqProtSetProperties.setSelectedMasterQuantPeptideIds(selectedMQPepIds);
//		
//		
//		String keyMQProtSetProfile = profileByGrpSetupJson.keySet().iterator().next().toString();	
//		List<MasterQuantProteinSetProfile> mqPrSetProfiles = new ArrayList<MasterQuantProteinSetProfile>();
//		
//		List mqPProfileAsJson = (List) profileByGrpSetupJson.get(keyMQProtSetProfile); //assume 1 entry!
//		for(int index = 0; index <mqPProfileAsJson.size();index++){
//			List<Float> profileAbundances = (List<Float>) ((Map)mqPProfileAsJson.get(index)).get("abundances");
//			List<Long> profileMQPepIds = (List<Long>) ((Map)mqPProfileAsJson.get(index)).get("mq_peptide_ids");
//			List<Map<String,Object>> profileRatiosJson = (List<Map<String,Object>>) ((Map)mqPProfileAsJson.get(index)).get("ratios");
//			List<ComputedRatio> profileComputedRatios = null;
//			if(profileRatiosJson != null) {
//				profileComputedRatios = new ArrayList<ComputedRatio>();
//				for(int i =0; i<profileRatiosJson.size(); i++){
//					Map<String,Object> ratioMap = (Map) profileRatiosJson.get(i);
//					ComputedRatio cr = new ComputedRatio();
//					cr.setNumerator(Float.valueOf(ratioMap.get("numerator").toString()));
//					cr.setDenominator(Float.valueOf(ratioMap.get("denominator").toString()));
//					cr.setState(Integer.valueOf(ratioMap.get("state").toString()));
//					if(ratioMap.containsKey("t_test_p_value")){
//						cr.settTestPValue(Double.valueOf(ratioMap.get("t_test_p_value").toString()));
//					}
//					if(ratioMap.containsKey("t_test_p_value")){
//						cr.setzTestPValue(Double.valueOf(ratioMap.get("z_test_p_value").toString()));
//					}
//					profileComputedRatios.add(cr);
//				}		
//			}
//			
//			
//			MasterQuantProteinSetProfile mqPSProfile = new MasterQuantProteinSetProfile();
//			mqPSProfile.setAbundances(profileAbundances);
//			mqPSProfile.setMqPeptideIds(profileMQPepIds);
//			mqPSProfile.setRatios(profileComputedRatios);
//			mqPrSetProfiles.add(mqPSProfile);
//		}
//		
//		HashMap<String, List<MasterQuantProteinSetProfile>> mqProtSetProfilesByGroupSetupNumber = new HashMap<String, List<MasterQuantProteinSetProfile>>();
//		mqProtSetProfilesByGroupSetupNumber.put(keyMQProtSetProfile, mqPrSetProfiles);
//		mqProtSetProperties.setMqProtSetProfilesByGroupSetupNumber(mqProtSetProfilesByGroupSetupNumber);
		
		return mqProtSetProperties;
	}




}
