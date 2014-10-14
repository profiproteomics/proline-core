package fr.proline.core.orm.uds.dto;


import java.util.List;

import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.uds.Aggregation;
import fr.proline.core.orm.uds.QuantitationMethod;
import fr.proline.core.orm.uds.Dataset.DatasetType;
import fr.proline.core.orm.uds.Project;

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
}
