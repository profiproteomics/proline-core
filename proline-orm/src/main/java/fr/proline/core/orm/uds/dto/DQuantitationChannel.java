package fr.proline.core.orm.uds.dto;

import fr.proline.core.orm.msi.ResultSet;
import fr.proline.core.orm.uds.*;

/**
 * QuantitationChannel representation, This class enhance the inherited QuantitationChannel with some additional information
 *
 * @author MB243701
 */

public class DQuantitationChannel extends QuantitationChannel {

  private static final long serialVersionUID = 1L;

  /**
   * MsiSearch.resultFileName corresponding to the resultSummary.resultSet
   */
  private String resultFileName;

  /**
   * raw file Name : peaklist.path
   */
  private String rawFilePath;

  /**
   * raw file Name : run.rawFile.identifier
   */
  private String rawFileIdentifier;


  /**
   * link with the raw map id
   */
  private Long lcmsRawMapId;

  /**
   * mzdb file name
   */
  private String mzdbFileName;

  /**
   * associated resultSet from the identification
   */
  private ResultSet identRs;

  /**
   * associated identDataset id, if exists
   */
  private Long identDatasetId;

  /**
   * The Id of the BiologicalGroup this quant channel belongs to
   */
  private Long biologicalGroupId = null;

  /**
   * The name of the BiologicalGroup this quant channel belongs to
   */
  private String biologicalGroupName = null;

  /**
   * The fullName of this QuantitationChannel composed of the biologicalGroup name and the quantChannel name
   */
  private String fullName;

  public DQuantitationChannel(QuantitationChannel o) {
    super();
    setId(o.getId());
    setContextKey(o.getContextKey());
    setIdentResultSummaryId(o.getIdentResultSummaryId());
    setLcmsMapId(o.getLcmsMapId());
    setNumber(o.getNumber());
    setRun(o.getRun());
    setName(o.getName());
    setSerializedProperties(o.getSerializedProperties());
    setBiologicalSample(o.getBiologicalSample());
    setLabel(o.getQuantitationLabel());
    setQuantitationDataset(o.getQuantitationDataset());
    setMasterQuantitationChannel(o.getMasterQuantitationChannel());
    setSampleReplicate(o.getSampleReplicate());
  }

  @Override public String getName() {
    return super.getName();
  }

  public String getFullName() {
    return (fullName != null) ? fullName : getName();
  }

  public String getResultFileName() {
    return resultFileName;
  }

  public void setResultFileName(String resultFileName) {
    this.resultFileName = resultFileName;
  }

  public String getRawFilePath() {
    return rawFilePath;
  }

  public void setRawFilePath(String rawFilePath) {
    this.rawFilePath = rawFilePath;
  }

  public String getRawFileIdentifier() {
    return rawFileIdentifier;
  }

  public void setRawFileIdentifier(String rawFileIdentifier) {
    this.rawFileIdentifier = rawFileIdentifier;
  }
  
  public Long getLcmsRawMapId() {
    return lcmsRawMapId;
  }

  public void setLcmsRawMapId(Long lcmsRawMapId) {
    this.lcmsRawMapId = lcmsRawMapId;
  }

  public String getMzdbFileName() {
    return mzdbFileName;
  }

  public void setMzdbFileName(String mzdbFileName) {
    this.mzdbFileName = mzdbFileName;
  }

  public ResultSet getIdentRs() {
    return identRs;
  }

  public void setIdentRs(ResultSet identRs) {
    this.identRs = identRs;
  }

  public Long getIdentDatasetId() {
    return identDatasetId;
  }

  public void setIdentDatasetId(Long identDatasetId) {
    this.identDatasetId = identDatasetId;
  }

  public Long getBiologicalGroupId() {
    return biologicalGroupId;
  }

  public void setBiologicalGroupId(Long biologicalGroupId) {
    this.biologicalGroupId = biologicalGroupId;
  }

  public String getBiologicalGroupName() {
    return biologicalGroupName;
  }

  public void setBiologicalGroupName(String biologicalGroupName) {
    this.biologicalGroupName = biologicalGroupName;
    fullName = new StringBuilder(biologicalGroupName).append('.').append(super.getName()).toString();
  }


  @Override
  public int hashCode() {
    return Long.valueOf(getId()).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DQuantitationChannel that = (DQuantitationChannel) o;
    return this.getId() == that.getId();
  }

}
