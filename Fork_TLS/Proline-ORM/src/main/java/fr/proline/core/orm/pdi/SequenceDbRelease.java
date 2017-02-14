package fr.proline.core.orm.pdi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * The persistent class for the seq_db_release database table.
 * 
 */
@Entity
@Table(name = "seq_db_release")
public class SequenceDbRelease implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String date;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    private String version;

    public SequenceDbRelease() {
    }

    public long getId() {
	return this.id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getDate() {
	return this.date;
    }

    public void setDate(String date) {
	this.date = date;
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
