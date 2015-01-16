package fr.proline.core.orm.uds.dto;

import fr.proline.core.orm.uds.QuantitationChannel;

/**
 * quantitationChannel  representation, completed with some information 
 * @author MB243701
 *
 */

public class DQuantitationChannel extends QuantitationChannel{

	private static final long serialVersionUID = 1L;
	
	/**
	 * MsiSearch.resultFileName corresponding to the resultSummary.resultSet
	 */
	private String resultFileName ;
	
	/**
	 * raw file Name : peaklist.path
	 */
	private String rawFilePath;
	
	// link with the raw map id
	private Long lcmsRawMapId;
    
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
		setLabel(o.getLabel());
		setQuantitationDataset(o.getQuantitationDataset());
		setMasterQuantitationChannel(o.getMasterQuantitationChannel());
		setSampleReplicate(o.getSampleReplicate());
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
	
	/**
	 * returns the rawFileName form the rawFilePath, separated with \
	 * @return
	 */
	public  String getRawFileName() {
		if (this.rawFilePath == null) {
			return "";
		}else {
			int index = this.rawFilePath.lastIndexOf("\\");
			if (index == -1 || index == this.rawFilePath.length()-1) {
				return this.rawFilePath;
			}else {
				return this.rawFilePath.substring(index+1);
			}
		}
	}

	public Long getLcmsRawMapId() {
		return lcmsRawMapId;
	}

	public void setLcmsRawMapId(Long lcmsRawMapId) {
		this.lcmsRawMapId = lcmsRawMapId;
	}

}
