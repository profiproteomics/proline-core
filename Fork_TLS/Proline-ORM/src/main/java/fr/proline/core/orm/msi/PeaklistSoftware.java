package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the peaklist_software database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.PeaklistSoftware")
@NamedQueries({
	@NamedQuery(name = "findMsiPeaklistSoftForName", query = "select pls from fr.proline.core.orm.msi.PeaklistSoftware pls"
		+ " where (upper(pls.name) = :name) and (pls.version is null)"),

	@NamedQuery(name = "findMsiPeaklistSoftForNameAndVersion", query = "select pls from fr.proline.core.orm.msi.PeaklistSoftware pls"
		+ " where (upper(pls.name) = :name) and (upper(pls.version) = :version)")

})
@Table(name = "peaklist_software")
public class PeaklistSoftware implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    // MSI PeaklistSoftware Id are not generated (taken from Uds PeaklistSoftware entity)
    private long id;

    private String name;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    public PeaklistSoftware() {
    }

    /**
     * Create a Msi PeaklistSoftware entity from an Uds PeaklistSoftware entity. Created Msi PeaklistSoftware
     * entity shares the same Id with given Uds PeaklistSoftware.
     * 
     * @param udsPeakListSoftware
     *            PeaklistSoftware entity from udsDb used to initialize Msi PeaklistSoftware fields (must not
     *            be <code>null</code>)
     */
    public PeaklistSoftware(final fr.proline.core.orm.uds.PeaklistSoftware udsPeakListSoftware) {

	if (udsPeakListSoftware == null) {
	    throw new IllegalArgumentException("UdsPeakListSoftware is null");
	}

	setId(udsPeakListSoftware.getId());
	setName(udsPeakListSoftware.getName());

	final String udsPeakListSoftwareProps = udsPeakListSoftware.getSerializedProperties();

	if (StringUtils.isEmpty(udsPeakListSoftwareProps)) {
	    setSerializedProperties(null);
	} else {
	    setSerializedProperties(udsPeakListSoftwareProps);
	}

	final String udsPeakListSoftwareVersion = udsPeakListSoftware.getVersion();

	if (StringUtils.isEmpty(udsPeakListSoftwareVersion)) {
	    setVersion(null);
	} else {
	    setVersion(udsPeakListSoftwareVersion);
	}

    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
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
