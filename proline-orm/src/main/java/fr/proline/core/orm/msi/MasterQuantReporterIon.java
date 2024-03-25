package fr.proline.core.orm.msi;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.proline.core.orm.lcms.MapAlignment;
import fr.proline.core.orm.msi.dto.DMasterQuantPeptideIon;
import fr.proline.core.orm.msi.dto.DPeptideMatch;
import fr.proline.core.orm.msi.dto.DQuantReporterIon;
import fr.proline.core.orm.util.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name = "master_quant_reporter_ion")
public class MasterQuantReporterIon implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(MapAlignment.class);

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	@Column(name = "ms_query_id")
	private long msQueryId;

	@ManyToOne
	@JoinColumn(name = "master_quant_component_id")
	private MasterQuantComponent masterQuantComponent;

	@ManyToOne
	@JoinColumn(name = "master_quant_peptide_ion_id")
	private MasterQuantPeptideIon masterQuantPeptideIon;

	@ManyToOne
	@JoinColumn(name = "result_summary_id")
	private ResultSummary resultSummary;

	// Transient Variables not saved in database
	@Transient
	private TransientData transientData = null;

	public MasterQuantReporterIon() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public ResultSummary getResultSummary() {
		return resultSummary;
	}

	public void setResultSummary(ResultSummary resultSummary) {
		this.resultSummary = resultSummary;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public long getMsQueryId() {
		return msQueryId;
	}

	public void setMsQueryId(final long pMsQueryId) {
		msQueryId = pMsQueryId;
	}

	public MasterQuantComponent getMasterQuantComponent() {
		return masterQuantComponent;
	}

	public void setMasterQuantComponent(MasterQuantComponent masterQuantComponent) {
		this.masterQuantComponent = masterQuantComponent;
	}

	public MasterQuantPeptideIon getMasterQuantPeptideIon() {
		return masterQuantPeptideIon;
	}

	public void setMasterQuantPeptideIon(MasterQuantPeptideIon masterQuantPeptideIon) {
		this.masterQuantPeptideIon = masterQuantPeptideIon;
	}

	public TransientData getTransientData() {
		if (transientData == null) {
			transientData = new TransientData();
		}
		return transientData;
	}

	public void parseAnSetQuantReporterIonFromProperties(String quantReporterIonData) {
		Map<Long, DQuantReporterIon> quantReporterIonByQchIds = null;
		try {
			List<DQuantReporterIon> quantReporterIons = JsonSerializer.getMapper().readValue(quantReporterIonData, new TypeReference<List<DQuantReporterIon>>() {
			});

			quantReporterIonByQchIds = new HashMap<Long, DQuantReporterIon>();
			if (quantReporterIons != null) {
				for (int i = 0; i < quantReporterIons.size(); i++) {
					DQuantReporterIon nextQuantRepIon = quantReporterIons.get(i);
					if (nextQuantRepIon != null) {
						quantReporterIonByQchIds.put(nextQuantRepIon.getQuantChannelId(), nextQuantRepIon);
					}
				}
			}

		} catch (Exception e) {
			LOG.warn("Error Parsing DQuantReporterIon ", e);
			LOG.warn("quantReporterIonData= " + quantReporterIonData);
			quantReporterIonByQchIds = null;
		} finally {
			this.getTransientData().setQuantReporterIonByQchIds(quantReporterIonByQchIds);
		}
	}

	/**
	 * Transient Data which will be not saved in database Used by the Proline Studio IHM
	 *
	 * @author VD225637
	 */
	public static class TransientData implements Serializable {
		private static final long serialVersionUID = 1L;

		//Peptide Match quant value are associated to
		private DPeptideMatch peptideMatch;

		//MasterQuantPeptideIon this reporter ion is linked to
		private DMasterQuantPeptideIon masterQuantPeptideIon;

		private Map<Long, DQuantReporterIon> quantReporterIonByQchIds = null;

		public DMasterQuantPeptideIon getDMasterQuantPeptideIon() {
			return masterQuantPeptideIon;
		}

		public void setDMasterQuantPeptideIon(DMasterQuantPeptideIon masterQuantPeptideIon) {
			this.masterQuantPeptideIon = masterQuantPeptideIon;
		}

		public DPeptideMatch getPeptideMatch() {
			return peptideMatch;
		}

		public void setPeptideMatch(DPeptideMatch peptideMatch) {
			this.peptideMatch = peptideMatch;
		}

		public Map<Long, DQuantReporterIon> getQuantReporterIonByQchIds() {
			return quantReporterIonByQchIds;
		}

		public void setQuantReporterIonByQchIds(Map<Long, DQuantReporterIon> quantReporterIonByQchIds) {
			this.quantReporterIonByQchIds = quantReporterIonByQchIds;
		}
	}
}
