package fr.proline.core.orm.uds.dto;

import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.uds.Aggregation;
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
    private DatasetType m_type;
   
    private int m_childrenCount;
    private Long m_resultSetId;
    private Long m_resultSummaryId;
    private int m_number;
    private Aggregation m_aggregation;
    
    private ResultSummary m_resultSummary = null;
    private ResultSet m_resultSet = null;
    
    
    //JPM.TEST
    public DDataset(long id) {
        m_id = id;
    }
    
    public DDataset(long id, Project project, String name, DatasetType type, int childrenCount, Long resultSetId, Long resultSummaryId, int number, Aggregation aggregation) {
        m_id = id;
        m_project = project;
        m_name = name;
        m_type = type;
        m_childrenCount = childrenCount;
        m_resultSetId = resultSetId;
        m_resultSummaryId = resultSummaryId;
        m_number = number;
        m_aggregation = aggregation;
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
}
