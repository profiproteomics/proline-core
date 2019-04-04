package fr.proline.core.orm.uds.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.proline.core.orm.uds.Dataset;
import fr.proline.core.orm.util.JsonSerializer;

/**
 * Light MasterQuantitationChannel representation
 *
 */
public class DMasterQuantitationChannel {

	private long id;

	private String name;

	private Long quantResultSummaryId;

	private List<DQuantitationChannel> quantitationChannels;

	private Dataset dataset;

	private String serializedProperties;

	// serializedProperties as a map
	private Map<String, Object> serializedPropertiesMap;

	// object corresponding to the ident_dataset_id stored in the serialized properties
	private DDataset identDataset;

	private Long identResultSummaryId;

	private Map<Integer, List<DQuantitationChannel>> groups;

	public DMasterQuantitationChannel() {

	}

	public DMasterQuantitationChannel(
		long m_id,
		String m_name,
		Long quantResultSummaryId,
		List<DQuantitationChannel> qChannels,
		Dataset dataset,
		String serializedProperties) {
		super();
		this.id = m_id;
		this.name = m_name;
		this.quantResultSummaryId = quantResultSummaryId;
		this.quantitationChannels = qChannels;
		this.dataset = dataset;
		this.serializedProperties = serializedProperties;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getQuantResultSummaryId() {
		return quantResultSummaryId;
	}

	public void setQuantResultSummaryId(Long quantResultSummaryId) {
		this.quantResultSummaryId = quantResultSummaryId;
	}

	public List<DQuantitationChannel> getQuantitationChannels() {
		return quantitationChannels;
	}

	public void setQuantitationChannels(List<DQuantitationChannel> quantitationChannels) {
		this.quantitationChannels = quantitationChannels;
		groups = new HashMap<>();
		for (DQuantitationChannel channel : this.quantitationChannels) {

			Integer groupNumber = Integer.valueOf(channel.getContextKey().split("\\.")[0]);
			if (!groups.containsKey(groupNumber)) {
				groups.put(groupNumber, new ArrayList<>());
			}
			groups.get(groupNumber).add(channel);
		}
	}

	public int getGroupsCount() {
		return groups == null ? 0 : groups.size();
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public String getSerializedProperties() {
		return serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Map<String, Object> getSerializedPropertiesMap() {
		return serializedPropertiesMap;
	}

	public void setSerializedPropertiesMap(
		Map<String, Object> serializedPropertiesMap) {
		this.serializedPropertiesMap = serializedPropertiesMap;
	}

	public DDataset getIdentDataset() {
		return identDataset;
	}

	public void setIdentDataset(DDataset identDataset) {
		this.identDataset = identDataset;
	}

	public Long getIdentResultSummaryId() {
		return identResultSummaryId;
	}

	public void setIdentResultSummaryId(Long identRsmId) {
		this.identResultSummaryId = identRsmId;
	}

	public List<DQuantitationChannel> getQuantitationChannels(Integer groupNumber) {
		return groups.get(groupNumber);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
		if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
			serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(), Map.class);
		}
		return serializedPropertiesMap;
	}

	public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
		this.serializedPropertiesMap = serializedPropertiesMap;
		this.serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
	}

}
