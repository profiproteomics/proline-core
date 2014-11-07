package fr.proline.core.orm.lcms;

import java.io.Serializable;

import javax.persistence.*;


/**
 * The persistent class for the instrument database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.lcms.Instrument")
@NamedQuery(name="Instrument.findAll", query="SELECT i FROM Instrument i")
public class Instrument  {
	private static final long serialVersionUID = 1L;

	@Id
	private Long id;

	private String name;

	@Column(name="serialized_properties")
	private String serializedProperties;

	private String source;

	public Instrument() {
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getSource() {
		return this.source;
	}

	public void setSource(String source) {
		this.source = source;
	}

}