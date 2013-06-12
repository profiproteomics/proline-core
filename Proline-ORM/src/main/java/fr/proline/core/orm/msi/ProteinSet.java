package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import fr.proline.util.StringUtils;

/**
 * The persistent class for the protein_set database table.
 * 
 */
@Entity
@Table(name = "protein_set")
public class ProteinSet implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "is_validated")
    private boolean isValidated;

    @Column(name = "master_quant_component_id")
    private Long masterQuantComponentId;

    @ManyToOne
    @JoinColumn(name = "result_summary_id")
    private ResultSummary resultSummary;

    @Column(name = "selection_level")
    private int selectionLevel;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // bi-directional one-to-one association to PeptideSet
    // @OneToOne(mappedBy="proteinSet",fetch = FetchType.LAZY)
    // private PeptideSet peptideOverSet;

    @Column(name = "typical_protein_match_id")
    private long typicalProteinMatchId;

    // bi-directional many-to-one association to ProteinSetProteinMatchItem
    @OneToMany(mappedBy = "proteinSet")
    private Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems;

    @ElementCollection
    @MapKeyColumn(name = "schema_name")
    @Column(name = "object_tree_id")
    @CollectionTable(name = "protein_set_object_tree_map", joinColumns = @JoinColumn(name = "protein_set_id", referencedColumnName = "id"))
    private Map<String, Long> objectTreeIdByName;

    // Transient data not saved in database
    @Transient
    private TransientData transientData = null;

    public ProteinSet() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public boolean getIsValidated() {
	return isValidated;
    }

    public void setIsValidated(final boolean pIsValidated) {
	isValidated = pIsValidated;
    }

    public Long getMasterQuantComponentId() {
	return masterQuantComponentId;
    }

    public void setMasterQuantComponentId(final Long pMasterQuantComponentId) {
	masterQuantComponentId = pMasterQuantComponentId;
    }

    public ResultSummary getResultSummary() {
	return this.resultSummary;
    }

    public void setResultSummary(ResultSummary resultSummary) {
	this.resultSummary = resultSummary;
    }

    public int getSelectionLevel() {
	return selectionLevel;
    }

    public void setSelectionLevel(final int pSelectionLevel) {
	selectionLevel = pSelectionLevel;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    /*
     * public PeptideSet getPeptideOverSet() { return null; //this.peptideOverSet; }
     * 
     * public void setPeptideOverSet(PeptideSet peptideSet) { //this.peptideOverSet = peptideSet; }
     */

    public long getProteinMatchId() {
	return typicalProteinMatchId;
    }

    public void setProteinMatchId(final long pProteinMatchId) {
	typicalProteinMatchId = pProteinMatchId;
    }

    public Set<ProteinSetProteinMatchItem> getProteinSetProteinMatchItems() {
	return this.proteinSetProteinMatchItems;
    }

    public void setProteinSetProteinMatchItems(Set<ProteinSetProteinMatchItem> proteinSetProteinMatchItems) {
	this.proteinSetProteinMatchItems = proteinSetProteinMatchItems;
    }

    void setObjectTreeIdByName(final Map<String, Long> objectTree) {
	objectTreeIdByName = objectTree;
    }

    public Map<String, Long> getObjectTreeIdByName() {
	return objectTreeIdByName;
    }

    public Long putObject(final String schemaName, final long objectId) {

	if (StringUtils.isEmpty(schemaName)) {
	    throw new IllegalArgumentException("Invalid schemaName");
	}

	Map<String, Long> localObjectTree = getObjectTreeIdByName();

	if (localObjectTree == null) {
	    localObjectTree = new HashMap<String, Long>();

	    setObjectTreeIdByName(localObjectTree);
	}

	return localObjectTree.put(schemaName, Long.valueOf(objectId));
    }

    public Long removeObject(final String schemaName) {
	Long result = null;

	final Map<String, Long> localObjectTree = getObjectTreeIdByName();
	if (localObjectTree != null) {
	    result = localObjectTree.remove(schemaName);
	}

	return result;
    }

    public TransientData getTransientData() {
	if (transientData == null) {
	    transientData = new TransientData();
	}
	return transientData;
    }

    /**
     * Transient Data which will be not saved in database Used by the Proline Studio IHM
     * 
     * @author JM235353
     */
    public static class TransientData implements Serializable {
	private static final long serialVersionUID = 1L;

	private ProteinMatch typicalProteinMatch = null;
	private ProteinMatch[] sameSet = null; // loaded later than sameSetCount
	private ProteinMatch[] subSet = null; // loaded later than subSetCount
	private Integer spectralCount = null;
	private Integer specificSpectralCount = null;
	private Integer sameSetCount = null;
	private Integer subSetCount = null;

	protected TransientData() {
	}

	public ProteinMatch getTypicalProteinMatch() {
	    return typicalProteinMatch;
	}

	public void setTypicalProteinMatch(ProteinMatch p) {
	    typicalProteinMatch = p;
	}

	public ProteinMatch[] getSameSet() {
	    return sameSet;
	}

	public void setSameSet(ProteinMatch[] sameSet) {
	    this.sameSet = sameSet;
	}

	public ProteinMatch[] getSubSet() {
	    return subSet;
	}

	public void setSubSet(ProteinMatch[] subSet) {
	    this.subSet = subSet;
	}

	public Integer getSpectralCount() {
	    return spectralCount;
	}

	public void setSpectralCount(Integer spectralCount) {
	    this.spectralCount = spectralCount;
	}

	public Integer getSpecificSpectralCount() {
	    return specificSpectralCount;
	}

	public void setSpecificSpectralCount(Integer specificSpectralCount) {
	    this.specificSpectralCount = specificSpectralCount;
	}

	public Integer getSameSetCount() {
	    return sameSetCount;
	}

	public void setSameSetCount(Integer sameSetCount) {
	    this.sameSetCount = sameSetCount;
	}

	public Integer getSubSetCount() {
	    return subSetCount;
	}

	public void setSubSetCount(Integer subSetCount) {
	    this.subSetCount = subSetCount;
	}

    }

}
