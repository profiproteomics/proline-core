package fr.proline.core.orm.msi.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.proline.core.orm.msi.MasterQuantPeptideIon;
import fr.proline.core.orm.util.JsonSerializer;

/**
 * Infer DMasterQuantPeptide representation from MasterQuantComponent table
 * for those linked to peptideInstance. 
 * ObjectTree Schema = ""object_tree.label_free_quant_peptides""
 *
 */
public class DMasterQuantPeptide {

	private static final Logger LOG = LoggerFactory.getLogger(DMasterQuantPeptide.class);

	/* MasterQuantComponent fields */
	private long m_id; //id of associated MasterQuantComponent

	private long m_peptideInstanceId;

	private int m_selectionLevel;

	private long m_objectTreeId;

	private String m_serializedProperties;

	private Long m_quantResultSummaryId;

	// deserialized Properties 
	private MasterQuantPeptideProperties m_mqPeptideProperties;

	//List of QuantPPeptide ... to be loaded before use 
	protected Map<Long, DQuantPeptide> m_quantPeptideByQchIds = new HashMap<Long, DQuantPeptide>();

	//Associated MasterQuantPeptideIons
	List<MasterQuantPeptideIon> m_masterQPepIons;

	//DPeptideInstance to provide access to "Identification" Best DPeptideMatch
	DPeptideInstance m_dPeptideInstance;

	DPeptideMatch m_representativePepMatch;

	// Cluster
	DCluster m_cluster;

	protected DMasterQuantPeptide() {
		super();
	}

	public DMasterQuantPeptide(
			long id,
			int selectionLevel,
			long objectTreeId,
			String serializedProperties,
			Long quantResultSummaryId) {
		super();
		m_id = id;
		m_selectionLevel = selectionLevel;
		m_objectTreeId = objectTreeId;
		m_serializedProperties = serializedProperties;
		m_quantResultSummaryId = quantResultSummaryId;
	}

	public Map<Long, DQuantPeptide> parseQuantPeptideFromProperties(String quantPeptideData) {

		try {
			ObjectMapper objectMapper = JsonSerializer.getMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			List<DQuantPeptide> quantPeptides = objectMapper.readValue(quantPeptideData, new TypeReference<List<DQuantPeptide>>() {
			});

			m_quantPeptideByQchIds = new HashMap<Long, DQuantPeptide>();
			for (int i = 0; i < quantPeptides.size(); i++) {
				DQuantPeptide nextQuantPeptide = quantPeptides.get(i);
				if (nextQuantPeptide != null) {
					m_quantPeptideByQchIds.put(nextQuantPeptide.quantChannelId, nextQuantPeptide);
				}
			}

		} catch (Exception e) {
			LOG.warn("Error Parsing DQuantPeptide ", e);
			m_quantPeptideByQchIds = null;
		}

		return m_quantPeptideByQchIds;
	}

	public long getId() {
		return m_id;
	}

	public void setId(long m_id) {
		this.m_id = m_id;
	}

	public long getPeptideInstanceId() {
		return m_peptideInstanceId;
	}

	public void setPeptideInstanceId(long peptideInstanceId) {
		this.m_peptideInstanceId = peptideInstanceId;
	}

	public int getSelectionLevel() {
		return m_selectionLevel;
	}

	public void setSelectionLevel(int selectionLevel) {
		this.m_selectionLevel = selectionLevel;
	}

	public long getObjectTreeId() {
		return m_objectTreeId;
	}

	public void setObjectTreeId(long objectTreeId) {
		this.m_objectTreeId = objectTreeId;
	}

	public String getSerializedProperties() {
		return m_serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.m_serializedProperties = serializedProperties;
		this.m_mqPeptideProperties = null;
	}

	public Long getQuantResultSummaryId() {
		return m_quantResultSummaryId;
	}

	public void setQuantResultSummaryId(Long quantResultSummaryId) {
		this.m_quantResultSummaryId = quantResultSummaryId;
	}

	public MasterQuantPeptideProperties getMasterQuantPeptideProperties() {
		if ((m_mqPeptideProperties == null) && (m_serializedProperties != null)) {
			try {
				//	Parse properties to get Values
				ObjectMapper objectMapper = JsonSerializer.getMapper();
				m_mqPeptideProperties = objectMapper.readValue(m_serializedProperties, MasterQuantPeptideProperties.class);
			} catch (Exception e) {
				LOG.warn(" Error parsiong MasterQuantPeptideProperties ", e);

			}

		}
		return m_mqPeptideProperties;
	}

	public void setMasterQuantPeptideProperties(MasterQuantPeptideProperties mqPeptideProperties) {
		this.m_mqPeptideProperties = mqPeptideProperties;
	}

	public Map<Long, DQuantPeptide> getQuantPeptideByQchIds() {
		return m_quantPeptideByQchIds;
	}

	public void setQuantPeptideByQchIds(Map<Long, DQuantPeptide> quantPeptideByQchIds) {
		this.m_quantPeptideByQchIds = quantPeptideByQchIds;
	}

	public List<MasterQuantPeptideIon> getMasterQPepIons() {
		return m_masterQPepIons;
	}

	public void setMasterQPepIons(List<MasterQuantPeptideIon> masterQPepIons) {
		this.m_masterQPepIons = masterQPepIons;
	}

	public DPeptideInstance getPeptideInstance() {
		return m_dPeptideInstance;
	}

	public void setPeptideInstance(DPeptideInstance peptideInstance) {
		this.m_dPeptideInstance = peptideInstance;
	}

	public DPeptideMatch getRepresentativePepMatch() {
		if(m_representativePepMatch == null && m_dPeptideInstance != null)
			return m_dPeptideInstance.getBestPeptideMatch();
		return m_representativePepMatch;
	}

	public void setRepresentativePepMatch(DPeptideMatch representativePepMatch) {
		this.m_representativePepMatch = representativePepMatch;
	}

	public DCluster getCluster() {
		return this.m_cluster;
	}

	public void setCluster(DCluster cluster) {
		this.m_cluster = cluster;
	}
}
