package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

/**
 * The persistent class for the scoring database table.
 * 
 */
@Entity
@NamedQuery(name = "findScoringForScoreType", query = "select s from fr.proline.core.orm.msi.Scoring s"
	+ " where concat(concat(s.searchEngine, \':\'), s.name) = :scoreType")
public class Scoring implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String description;

    private String name;

    @Column(name = "search_engine")
    private String searchEngine;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    public Scoring() {
    }

    public long getId() {
	return id;
    }

    public void setId(final long pId) {
	id = pId;
    }

    public String getDescription() {
	return this.description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public String getName() {
	return this.name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getSearchEngine() {
	return this.searchEngine;
    }

    public void setSearchEngine(String searchEngine) {
	this.searchEngine = searchEngine;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

}
