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

}
