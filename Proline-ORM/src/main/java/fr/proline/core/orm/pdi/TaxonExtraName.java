package fr.proline.core.orm.pdi;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the taxon_extra_name database table.
 * 
 */
@Entity
@Table(name="taxon_extra_name")
public class TaxonExtraName implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="class")
	private String class_;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private Integer value;

	//bi-directional many-to-one association to Taxon
    @ManyToOne
	private Taxon taxon;

    public TaxonExtraName() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getClass_() {
		return this.class_;
	}

	public void setClass_(String class_) {
		this.class_ = class_;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Integer getValue() {
		return this.value;
	}

	public void setValue(Integer value) {
		this.value = value;
	}

	public Taxon getTaxon() {
		return this.taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
	}
	
}