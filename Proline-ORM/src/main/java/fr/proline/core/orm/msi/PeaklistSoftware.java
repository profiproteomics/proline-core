package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the peaklist_software database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.PeaklistSoftware")
@NamedQueries({
	@NamedQuery(name = "findMsiPeaklistSoftByName", query = "select pls from fr.proline.core.orm.msi.PeaklistSoftware pls"
		+ " where (lower(pls.name) = :name) and (pls.version is null)"),

	@NamedQuery(name = "findMsiPeaklistSoftByNameAndVersion", query = "select pls from fr.proline.core.orm.msi.PeaklistSoftware pls"
		+ " where (lower(pls.name) = :name) and (lower(pls.version) = :version)"),

})
@Table(name = "peaklist_software")
public class PeaklistSoftware implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    public PeaklistSoftware() {
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
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

    public String getVersion() {
	return this.version;
    }

    public void setVersion(String version) {
	this.version = version;
    }

}