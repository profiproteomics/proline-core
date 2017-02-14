package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.Map;
import javax.persistence.Transient;
import fr.profi.util.StringUtils;
import fr.proline.core.orm.util.JsonSerializer;

/**
 * The persistent class for the consensus_spectrum database table.
 * 
 */
@Entity
@Table(name = "master_quant_component")
public class MasterQuantComponent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "selection_level")
    private int selectionLevel;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "object_tree_id")
    private long objectTreeId;

    @ManyToOne
    @JoinColumn(name = "result_summary_id")
    private ResultSummary resultSummary;

    @OneToMany(mappedBy = "masterQuantComponent")
    private Set<MasterQuantPeptideIon> masterQuantPeptideIons;

    @OneToMany(mappedBy = "masterQuantComponent")
    private Set<MasterQuantReporterIon> masterQuantReporterIons;

    @Transient
    private Map<String, Object> serializedPropertiesMap;
    
    public MasterQuantComponent() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getSelectionLevel() {
	return selectionLevel;
    }

    public void setSelectionLevel(final int pSelectionLevel) {
	selectionLevel = pSelectionLevel;
    }

    public String getSchemaName() {
	return schemaName;
    }

    public void setSchemaName(String schemaName) {
	this.schemaName = schemaName;
    }

    public String getSerializedProperties() {
	return serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public long getObjectTreeId() {
	return objectTreeId;
    }

    public void setObjectTreeId(final long pObjectTreeId) {
	objectTreeId = pObjectTreeId;
    }

    public ResultSummary getResultSummary() {
	return resultSummary;
    }

    public void setResultSummary(ResultSummary resultSummary) {
	this.resultSummary = resultSummary;
    }

    public Set<MasterQuantPeptideIon> getMasterQuantPeptideIons() {
	return masterQuantPeptideIons;
    }

    public void setMasterQuantPeptideIons(Set<MasterQuantPeptideIon> masterQuantPeptideIons) {
	this.masterQuantPeptideIons = masterQuantPeptideIons;
    }

    public Set<MasterQuantReporterIon> getMasterQuantReporterIons() {
	return masterQuantReporterIons;
    }

    public void setMasterQuantReporterIons(Set<MasterQuantReporterIon> masterQuantReporterIons) {
	this.masterQuantReporterIons = masterQuantReporterIons;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSerializedPropertiesAsMap() throws Exception {
	if ((serializedPropertiesMap == null) && (serializedProperties != null)) {
	    serializedPropertiesMap = JsonSerializer.getMapper().readValue(getSerializedProperties(),
		    Map.class);
	}
	return serializedPropertiesMap;
    }

    public void setSerializedPropertiesAsMap(Map<String, Object> serializedPropertiesMap) throws Exception {
	this.serializedPropertiesMap = serializedPropertiesMap;
	this.serializedProperties = JsonSerializer.getMapper().writeValueAsString(serializedPropertiesMap);
    }

    
}
