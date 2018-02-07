package fr.proline.core.orm.uds.dto;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fr.proline.core.orm.lcms.MapAlignment;
import fr.proline.core.orm.lcms.MapAlignmentPK;
import fr.proline.core.orm.lcms.MapTime;
import fr.proline.core.orm.lcms.ProcessedMap;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.uds.Aggregation;
import fr.proline.core.orm.uds.Dataset.DatasetType;
import fr.proline.core.orm.uds.GroupSetup;
import fr.proline.core.orm.uds.ObjectTree;
import fr.proline.core.orm.uds.Project;
import fr.proline.core.orm.uds.QuantitationMethod;
import fr.proline.core.orm.util.JsonSerializer;

/**
 *
 * @author JM235353
 */
public class DDataset {
    private long m_id;
    private Project m_project; 
    private String m_name;
    private String m_description;
    private DatasetType m_type;
   
    private int m_childrenCount;
    private Long m_resultSetId;
    private Long m_resultSummaryId;
    private int m_number;
    private Aggregation m_aggregation;
    private QuantitationMethod m_quantitationMethod;
    
    private ResultSummary m_resultSummary = null;
    private ResultSet m_resultSet = null;

    private MergeInformation m_mergeInformation = MergeInformation.MERGE_UNKNOW;
    
    private List<DMasterQuantitationChannel> m_masterQuantitationChannels;
    private List<MapAlignment> m_mapAlignments;
    private List<MapAlignment> m_mapReversedAlignments;
    private List<ProcessedMap> m_maps;
    private Long m_alnReferenceMapId;
    
    private ObjectTree m_postQuantProcessingConfig;
    private ObjectTree m_quantProcessingConfig;
    
    // postQuantProcessingConfig as a map
 	private Map<String, Object> postQuantProcessingConfigMap;
 	// quantProcessingConfig as a map
  	private Map<String, Object> quantProcessingConfigMap;
  	
  	// Experimental design for quanti
  	private GroupSetup groupSetup;
    
    public enum MergeInformation {
    	MERGE_UNKNOW,
    	NO_MERGE,
    	MERGE_SEARCH_RESULT,
    	MERGE_IDENTIFICATION_SUMMARY
    }
    
    public DDataset(long id, Project project, String name, DatasetType type, int childrenCount, Long resultSetId, Long resultSummaryId, int number) {
        m_id = id;
        m_project = project;
        m_name = name;
        m_type = type;
        m_childrenCount = childrenCount;
        m_resultSetId = resultSetId;
        m_resultSummaryId = resultSummaryId;
        m_number = number;
        m_aggregation = null;
        m_quantitationMethod = null;
    }
    
    public long getId() {
        return m_id;
    }
    
    public Project getProject() {
        return m_project;
    }
    
    public DatasetType getType() {
        return m_type;
    }
    
    public String getName() {
        return m_name;
    }
    
    public void setName(String name) {
        m_name = name;
    }
    
    public String getDescription() {
    	return m_description;
    }
    
    public void setDescription(String description) {
    	this.m_description = description;
    }
    
    public int getChildrenCount() {
        return m_childrenCount;
    }
    
    public void setChildrenCount(int childrenCount) {
        m_childrenCount = childrenCount;
    }

    public Long getResultSetId() {
        return m_resultSetId;
    }
    
    public void setResultSetId(Long resultSetId) {
        m_resultSetId = resultSetId;
    }

    public Long getResultSummaryId() {
        return m_resultSummaryId;
    }
    
    public void setResultSummaryId(Long resultSummaryId) {
        m_resultSummaryId = resultSummaryId;
    }
    
    
    public int getNumber() {
        return m_number;
    }
    
    public Aggregation getAggregation() {
        return m_aggregation;
    }
    
    public void setAggregation(Aggregation aggregation) {
        m_aggregation = aggregation;
    }
    
    public QuantitationMethod getQuantitationMethod() {
        return m_quantitationMethod;
    }
    
    public void setQuantitationMethod(QuantitationMethod quantitationMethod) {
        m_quantitationMethod = quantitationMethod;
    }
    
    
    public ResultSummary getResultSummary() {
        return m_resultSummary;
    }

    public void setResultSummary(ResultSummary resultSummary) {
        this.m_resultSummary = resultSummary;
    }

    public ResultSet getResultSet() {
        return m_resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
        this.m_resultSet = resultSet;
    }
    
    public MergeInformation getMergeInformation() {
    	return m_mergeInformation;
    }
    public void setMergeInformation(MergeInformation mergeInformation) {
    	m_mergeInformation = mergeInformation;
    }
    
    public List<DMasterQuantitationChannel> getMasterQuantitationChannels() {
    	return m_masterQuantitationChannels;
    }

    public void setMasterQuantitationChannels(List<DMasterQuantitationChannel> masterQuantitationChannels) {
    	this.m_masterQuantitationChannels = masterQuantitationChannels;
    }

	public ObjectTree getPostQuantProcessingConfig() {
		return m_postQuantProcessingConfig;
	}

	public void setPostQuantProcessingConfig(
			ObjectTree postQuantProcessingConfig) {
		this.m_postQuantProcessingConfig = postQuantProcessingConfig;
	}

	public ObjectTree getQuantProcessingConfig() {
		return m_quantProcessingConfig;
	}

	public void setQuantProcessingConfig(ObjectTree quantProcessingConfig) {
		this.m_quantProcessingConfig = quantProcessingConfig;
	}
	
	
	// deserialize because the post quant processing config could have changed
	@SuppressWarnings("unchecked")
    public Map<String, Object> getPostQuantProcessingConfigAsMap() throws Exception {
		if ( (m_postQuantProcessingConfig != null)) {
			postQuantProcessingConfigMap = JsonSerializer.getMapper().readValue(getPostQuantProcessingConfig().getClobData(), Map.class);
    	}
    	return postQuantProcessingConfigMap;
    }
	
	@SuppressWarnings("unchecked")
    public Map<String, Object> getQuantProcessingConfigAsMap() throws Exception {
		if ((quantProcessingConfigMap == null) && (m_quantProcessingConfig != null)) {
			quantProcessingConfigMap = JsonSerializer.getMapper().readValue(getQuantProcessingConfig().getClobData(), Map.class);
    	}
    	return quantProcessingConfigMap;
    }

	public GroupSetup getGroupSetup() {
		return groupSetup;
	}

	public void setGroupSetup(GroupSetup groupSetup) {
		this.groupSetup = groupSetup;
	}
	

    public Long getAlnReferenceMapId() {
		return m_alnReferenceMapId;
	}

	public void setAlnReferenceMapId(Long alnReferenceMapId) {
		this.m_alnReferenceMapId = alnReferenceMapId;
	}

	public List<MapAlignment> getMapAlignments() {
        return m_mapAlignments;
    }

    public void setMapAlignments(List<MapAlignment> allMapAlignments) {
        this.m_mapAlignments = allMapAlignments;
        updateReversedAlignments();
    }

    public void clearMapAlignments() {
    	this.m_mapAlignments = null;
    	this.m_mapReversedAlignments = null;
    	this.m_alnReferenceMapId = -1L;
    }
    
    public List<ProcessedMap> getMaps() {
        return m_maps;
    }

    public void setMaps(List<ProcessedMap> allMaps) {
        this.m_maps = allMaps;
    }
    
	private void updateReversedAlignments() {
		// add the reversed alignments
		m_mapReversedAlignments = new ArrayList<>();
//		m_mapReversedAlignments.addAll(m_mapAlignments);
		for (MapAlignment ma : m_mapAlignments) {

			MapAlignment reversedMap = new MapAlignment();
			MapAlignmentPK mapKey = new MapAlignmentPK();
			mapKey.setFromMapId(ma.getDestinationMap().getId());
			mapKey.setToMapId(ma.getSourceMap().getId());
			mapKey.setMassStart(ma.getId().getMassStart());
			mapKey.setMassEnd(ma.getId().getMassEnd());
			reversedMap.setId(mapKey);
			reversedMap.setDestinationMap(ma.getSourceMap());
			reversedMap.setSourceMap(ma.getDestinationMap());
			reversedMap.setMapSet(ma.getMapSet());

			int nbLandmarks = ma.getMapTimeList().size();
			Double[] revTimeList = new Double[nbLandmarks];
			Double[] revDeltaTimeList = new Double[nbLandmarks];
			List<MapTime> revMapTimeList = new ArrayList<>();
			for (int i = 0; i < nbLandmarks; i++) {
				MapTime mapTime = ma.getMapTimeList().get(i);
				Double deltaTime = mapTime.getDeltaTime();
				Double targetMapTime = mapTime.getTime() + deltaTime;
				revTimeList[i] = targetMapTime;
				revDeltaTimeList[i] = -deltaTime;
				MapTime rmp = new MapTime(revTimeList[i], revDeltaTimeList[i]);
				revMapTimeList.add(rmp);
			}
			String deltaS = org.apache.commons.lang3.StringUtils.join(revDeltaTimeList, " ");
			String timeS = org.apache.commons.lang3.StringUtils.join(revTimeList, " ");

			reversedMap.setMapTimeList(revMapTimeList);
			reversedMap.setDeltaTimeList(deltaS);
			reversedMap.setTimeList(timeS);

			m_mapReversedAlignments.add(reversedMap);
		}
	}

    public List<MapAlignment> getMapReversedAlignments() {
        return m_mapReversedAlignments;
    }
    
    public List<MapAlignment> getMapAlignmentsFromMap(Long mapId) {
    	List<MapAlignment> list = new ArrayList<>();
    	for (MapAlignment ma : m_mapAlignments) {
    		if (ma.getSourceMap().getId().equals(mapId)) {
    			list.add(ma);
    		}
    	}

    	for (MapAlignment ma : m_mapReversedAlignments) {
    		if (ma.getSourceMap().getId().equals(mapId)) {
    			list.add(ma);
    		}    		
    	}
    	  	
        return list;
    }
    
    
	/**
	 * return true if the dataset is a XIC quantitation (type feature_intensity)
	 * @return
	 */
	public boolean isQuantiXIC(){
        return ( this.m_quantitationMethod != null && m_quantitationMethod.getAbundanceUnit().compareTo("feature_intensity") == 0 ) ;
	}
	
	public boolean isQuantiSC(){
        return ( m_quantitationMethod != null && m_quantitationMethod.getAbundanceUnit().compareTo("spectral_counts") == 0 ) ;
	}
	
}
