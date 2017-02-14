package fr.proline.core.orm.msi.dto;

import java.util.List;

/**
 * represents the cluster for a MasterQuantPeptide with the abundances list. We suppose that the abundances are sorted by quantChannels, on the ids??
 * @author MB243701
 *
 */
public class DCluster {

	/* for now there is no id, in Studio, we set a number*/
	private int clusterId;
	
	private List<Float> abundances;
	private List<ComputedRatio> ratios;
	
	
	public DCluster() {
		
	}


	public DCluster(int clusterId, List<Float> abundances,
			List<ComputedRatio> ratios) {
		super();
		this.clusterId = clusterId;
		this.abundances = abundances;
		this.ratios = ratios;
	}

	
	public int getClusterId() {
		return clusterId;
	}


	public void setClusterId(int clusterId) {
		this.clusterId = clusterId;
	}


	public List<Float> getAbundances() {
		return abundances;
	}


	public void setAbundances(List<Float> abundances) {
		this.abundances = abundances;
	}


	public List<ComputedRatio> getRatios() {
		return ratios;
	}


	public void setRatios(List<ComputedRatio> ratios) {
		this.ratios = ratios;
	}

}
