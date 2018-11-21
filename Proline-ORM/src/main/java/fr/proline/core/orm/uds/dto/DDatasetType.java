package fr.proline.core.orm.uds.dto;

import fr.proline.core.orm.MergeMode;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.msi.ResultSummary;
import fr.proline.core.orm.uds.*;

/**
 *  Gather all information related to the Dataset type
 */
public class DDatasetType {

  private Dataset.DatasetType m_type;
  private Aggregation m_aggregation;
  private AggregationInformation m_aggregationInformation = AggregationInformation.UNKNOWN;
  private QuantitationMethod m_quantitationMethod;
  private DDataset m_dataset;

  public enum AggregationInformation {
    UNKNOWN,
    NONE,
    SEARCH_RESULT_UNION,
    IDENTIFICATION_SUMMARY_UNION,
    SEARCH_RESULT_AGG,
    IDENTIFICATION_SUMMARY_AGG
  }

  public enum QuantitationMethodInfo {
    NONE,
    ATOM_LABELING,
    ISOBARIC_TAGGING,
    RESIDUE_LABELING,
    SPECTRAL_COUNTING,
    FEATURES_EXTRACTION
  }

  public DDatasetType(DDataset dataset, Dataset.DatasetType m_type, Aggregation m_aggregation, QuantitationMethod m_quantitationMethod) {
    this.m_dataset = dataset;
    this.m_type = m_type;
    this.m_aggregation = m_aggregation;
    this.m_quantitationMethod = m_quantitationMethod;
  }

  protected void setAggregation(Aggregation aggregation) {
    m_aggregation = aggregation;
  }

  protected Aggregation getAggregation() {
    return m_aggregation;
  }

  protected void setQuantitationMethod(QuantitationMethod quantitationMethod) {
      m_quantitationMethod = quantitationMethod;
  }

  protected QuantitationMethod getQuantitationMethod() {
    return m_quantitationMethod;
  }

  protected void setAggregationInformation(AggregationInformation aggregationInformation) {
    m_aggregationInformation = aggregationInformation;
  }

  public boolean isIdentification() {
    return (m_type == Dataset.DatasetType.IDENTIFICATION || m_type == Dataset.DatasetType.IDENTIFICATION_FOLDER || m_type == Dataset.DatasetType.AGGREGATE);
  }

  public boolean isQuantitation() {
    return (m_type == Dataset.DatasetType.QUANTITATION || m_type == Dataset.DatasetType.QUANTITATION_FOLDER);
  }

  public boolean isTrash() {
    return (m_type == Dataset.DatasetType.TRASH);
  }

  public boolean isFolder() {
    return (m_type == Dataset.DatasetType.IDENTIFICATION_FOLDER || m_type == Dataset.DatasetType.QUANTITATION_FOLDER);
  }

  public boolean isAggregation() {
    if (m_type == Dataset.DatasetType.AGGREGATE) {
      return true;
    } else if (m_dataset != null) {
      ObjectTree quantProcessingConfig = m_dataset.getQuantProcessingConfig();
      return ((isQuantitation() && quantProcessingConfig.getSchema().getName().equals(ObjectTreeSchema.SchemaName.AGGREGATION_QUANT_CONFIG.getKeyName())));
    }
    return false;
  }


  public AggregationInformation getAggregationInformation() {
    if(m_aggregationInformation == null || m_aggregationInformation.equals(AggregationInformation.UNKNOWN)) {
      m_aggregationInformation = AggregationInformation.UNKNOWN;

      //Not defined or unknown, try to getInfo
      try {
        ResultSet resultSet = m_dataset.getResultSet();
        ResultSummary resultSummary = m_dataset.getResultSummary();

        if(resultSet !=null && (resultSet.getMergeMode().equals(MergeMode.UNION) ||resultSet.getMergeMode().equals(MergeMode.AGGREGATION))) {
          switch (resultSet.getMergeMode()) {
          case UNION:
            m_aggregationInformation = AggregationInformation.SEARCH_RESULT_UNION;
            break;
          case AGGREGATION:
            m_aggregationInformation = AggregationInformation.SEARCH_RESULT_AGG;
            break;
          }
        } else if(resultSummary != null  && (resultSummary.getMergeMode().equals(MergeMode.UNION) || resultSummary.getMergeMode().equals(MergeMode.AGGREGATION))) {
          switch (resultSummary.getMergeMode()) {
          case UNION:
            m_aggregationInformation = AggregationInformation.IDENTIFICATION_SUMMARY_UNION;
            break;
          case AGGREGATION:
            m_aggregationInformation = AggregationInformation.IDENTIFICATION_SUMMARY_AGG;
            break;
          }
        } else if(resultSet != null && resultSummary != null && resultSet.getMergeMode().equals(MergeMode.NO_MERGE) && resultSummary.getMergeMode().equals(MergeMode.NO_MERGE)){
          m_aggregationInformation =  AggregationInformation.NONE;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return m_aggregationInformation;
  }

  public QuantitationMethodInfo getQuantMethodInfo() {
    if (m_quantitationMethod == null)
      return QuantitationMethodInfo.NONE;
    if (m_quantitationMethod.getAbundanceUnit().equalsIgnoreCase("feature_intensity"))
      return QuantitationMethodInfo.FEATURES_EXTRACTION;
    if (m_quantitationMethod.getAbundanceUnit().equalsIgnoreCase("spectral_counts"))
      return QuantitationMethodInfo.SPECTRAL_COUNTING;

    return QuantitationMethodInfo.valueOf(m_quantitationMethod.getType().toLowerCase());
  }

}
