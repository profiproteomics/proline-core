package fr.proline.core.orm.uds.dto;

import fr.proline.core.orm.lcms.MapAlignment;
import fr.proline.core.orm.lcms.MapAlignmentPK;
import fr.proline.core.orm.lcms.MapTime;
import fr.proline.core.orm.lcms.ProcessedMap;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.uds.*;
import fr.proline.core.orm.uds.Dataset.DatasetType;
import fr.proline.core.orm.uds.dto.DDatasetType.AggregationInformation;
import fr.proline.core.orm.uds.dto.DDatasetType.QuantitationMethodInfo;
import fr.proline.core.orm.util.JsonSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author JM235353
 */
public class DDataset {

  private long m_id;
  private Project m_project;
  private String m_name;
  private String m_description;
  private int m_number;

  private int m_childrenCount;
  private Long m_resultSetId;
  private Long m_resultSummaryId;
  private ResultSummary m_resultSummary = null;
  private ResultSet m_resultSet = null;
  private DDatasetType m_type = null;

  private List<DMasterQuantitationChannel> m_masterQuantitationChannels;
  private List<MapAlignment> m_mapAlignments;
  private List<MapAlignment> m_mapReversedAlignments;
  private List<ProcessedMap> m_maps;
  private Long m_alnReferenceMapId;

  private ObjectTree m_postQuantProcessingConfig;
  private ObjectTree m_quantProcessingConfig;
  private Map<String, Object> postQuantProcessingConfigMap;
  private Map<String, Object> quantProcessingConfigMap;

  private GroupSetup groupSetup;

  public DDataset(long id, Project project, String name, DatasetType type, int childrenCount, Long resultSetId, Long resultSummaryId, int number) {
    m_id = id;
    m_project = project;
    m_name = name;
    m_childrenCount = childrenCount;
    m_resultSetId = resultSetId;
    m_resultSummaryId = resultSummaryId;
    m_number = number;
    m_type = new DDatasetType(this, type, null, null);
  }

  public long getId() {
    return m_id;
  }

  public Project getProject() {
    return m_project;
  }

  public DDatasetType getType() {
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

  public boolean isIdentification() {
    return m_type.isIdentification();
  }

  public boolean isQuantitation() {
    return m_type.isQuantitation();
  }

  public boolean isTrash() {
    return m_type.isTrash();
  }

  public boolean isFolder() {
    return m_type.isFolder();
  }

  public boolean isAggregation() {
    return m_type.isAggregation();
  }

  public void setAggregation(Aggregation aggregation) {
    m_type.setAggregation(aggregation);
  }

  public Aggregation getAggregation() {
    return m_type.getAggregation();
  }

  public AggregationInformation getAggregationInformation() {
    return m_type.getAggregationInformation();
  }

  public void setAggregationInformation(AggregationInformation aggregationInformation) {
    m_type.setAggregationInformation(aggregationInformation);
  }

  public QuantitationMethodInfo getQuantMethodInfo() {
    return m_type.getQuantMethodInfo();
  }

  public QuantitationMethod getQuantitationMethod() {
    return m_type.getQuantitationMethod();
  }

  public void setQuantitationMethod(QuantitationMethod quantitationMethod) {
    m_type.setQuantitationMethod(quantitationMethod);
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

  public List<DMasterQuantitationChannel> getMasterQuantitationChannels() {
    return m_masterQuantitationChannels;
  }

  public void setMasterQuantitationChannels(List<DMasterQuantitationChannel> masterQuantitationChannels) {
    this.m_masterQuantitationChannels = masterQuantitationChannels;
  }

  public void setObjectTree(ObjectTree ot) {
    if (ot != null) {
      if (ot.getSchema().getName().equalsIgnoreCase(ObjectTreeSchema.SchemaName.POST_QUANT_PROCESSING_CONFIG.getKeyName())) {
        this.m_postQuantProcessingConfig = ot;
        this.postQuantProcessingConfigMap = null;//should reinit map with new object tree
      } else if (ot.getSchema().getName().startsWith("quantitation")) {
        this.m_quantProcessingConfig = ot;
        this.quantProcessingConfigMap = null;//should reinit map with new object tree

      }
    }
  }

  public ObjectTree getPostQuantProcessingConfig() {
    return m_postQuantProcessingConfig;
  }

  public ObjectTree getQuantProcessingConfig() {
    return m_quantProcessingConfig;
  }

  // deserialize because the post quant processing config could have changed
  public Map<String, Object> getPostQuantProcessingConfigAsMap() throws Exception {
    if ((postQuantProcessingConfigMap == null) && (m_postQuantProcessingConfig != null)) {
      postQuantProcessingConfigMap = JsonSerializer.getMapper().readValue(getPostQuantProcessingConfig().getClobData(), Map.class);
    }
    return postQuantProcessingConfigMap;
  }

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
      reversedMap.setSerializedProperties(ma.getSerializedProperties());

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

}
