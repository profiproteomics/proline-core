package fr.proline.core.orm.uds;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * The persistent class for the quant_method database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findQuantMethodForTypeAndUnit", query = "select qm from fr.proline.core.orm.uds.QuantitationMethod qm"
		+ " where (upper(qm.type) = :searchType) and (upper(qm.abundanceUnit) = :searchAbundanceUnit)")
})
@Table(name = "quant_method")
public class QuantitationMethod implements Serializable {

    private static final long serialVersionUID = 1L;
    
    public enum Type {
      ATOM_LABELING("atom_labeling"),
      // TODO: rename isobaric_tagging in the UDSdb
      ISOBARIC_TAG("isobaric_tag"),
      LABEL_FREE("label_free"),
      RESIDUE_LABELING("residue_labeling");
      
      private final String type;
      
      private Type(final String type) {
          this.type = type;
      }
      
      @Override
      public String toString() {
          return type;
      }
    };

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "abundance_unit")
    private String abundanceUnit;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String type;

    // bi-directional many-to-one association to QuantLabel
    @OneToMany(mappedBy = "method")
    private Set<QuantitationLabel> labels;

    public QuantitationMethod() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getAbundanceUnit() {
	return this.abundanceUnit;
    }

    public void setAbundanceUnit(String abundanceUnit) {
	this.abundanceUnit = abundanceUnit;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getType() {
	return this.type;
    }

    public void setType(String type) {
	this.type = type;
    }

    public Set<QuantitationLabel> getLabels() {
	return this.labels;
    }

    public void setLabels(Set<QuantitationLabel> labels) {
	this.labels = labels;
    }

}
