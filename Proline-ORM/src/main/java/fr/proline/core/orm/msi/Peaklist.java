package fr.proline.core.orm.msi;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

/**
 * The persistent class for the peaklist database table.
 * 
 */
@Entity
public class Peaklist implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "ms_level")
    private int msLevel;

    private String path;

    @Column(name = "raw_file_name")
    private String rawFileName;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    @Column(name = "spectrum_data_compression")
    private String spectrumDataCompression;

    private String type;

    // uni-directional many-to-one association to PeaklistSoftware
    @ManyToOne
    @JoinColumn(name = "peaklist_software_id")
    private PeaklistSoftware peaklistSoftware;

    @OneToMany
    @JoinTable(name = "peaklist_relation", joinColumns = @JoinColumn(name = "parent_peaklist_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "child_peaklist_id", referencedColumnName = "id"))
    private Set<Peaklist> children;

    public Peaklist() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public int getMsLevel() {
	return msLevel;
    }

    public void setMsLevel(final int pMsLevel) {
	msLevel = pMsLevel;
    }

    public String getPath() {
	return this.path;
    }

    public void setPath(String path) {
	this.path = path;
    }

    public String getRawFileName() {
	return this.rawFileName;
    }

    public void setRawFileName(String rawFileName) {
	this.rawFileName = rawFileName;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

    public String getSpectrumDataCompression() {
	return this.spectrumDataCompression;
    }

    public void setSpectrumDataCompression(String spectrumDataCompression) {
	this.spectrumDataCompression = spectrumDataCompression;
    }

    public String getType() {
	return this.type;
    }

    public void setType(String type) {
	this.type = type;
    }

    public PeaklistSoftware getPeaklistSoftware() {
	return this.peaklistSoftware;
    }

    public void setPeaklistSoftware(PeaklistSoftware peaklistSoftware) {
	this.peaklistSoftware = peaklistSoftware;
    }

    public Set<Peaklist> getChildren() {
	return this.children;
    }

    public void setChildren(Set<Peaklist> children) {
	this.children = children;
    }

}
