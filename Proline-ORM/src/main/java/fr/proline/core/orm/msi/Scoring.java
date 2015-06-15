package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

import fr.profi.util.StringUtils;

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
    
    // TODO: change the hibernate definition of the class Scoring to use this enum ???
    // TODO: create a Java projects where we can share enumerations between OM and ORM ???
	public enum Type {

		MASCOT_IONS_SCORE(
			"mascot", "ions score",
			"The score provided for each Mascot peptide."
		),
		MASCOT_STANDARD_SCORE(
			"mascot", "standard score",
			"The score provided for each Mascot protein hit (it corresponds to the sum of ion scores)."
		),
		MASCOT_MUDPIT_SCORE(
			"mascot", "mudpit score",
			"The score provided for each Mascot protein hit when the number of MS/MS queries is high."
		),
		MASCOT_MODIFIED_MUDPIT_SCORE(
			"mascot", "modified mudpit score",
			"A modified version of the MudPIT score computed by Proline."
		),
		OMSSA_EVALUE(
			"omssa", "expect value",
			"The -log(E-value) provided by OMSSA for a peptide match."
		),
		COMET_EVALUE_LOG_SCALED(
			"comet", "evalue log scaled",
			"The -log(expectation value) provided by Comet for a peptide match."
		),
		MSGF_EVALUE_LOG_SCALED(
			"msgf", "evalue log scaled",
			"The -log(EValue) provided by MS-GF for a peptide match."
		),
		SEQUEST_EXPECT_LOG_SCALED(
			"sequest", "expect log scaled",
			"The -log(expect) provided by Sequest for a peptide match."
		),
		XTANDEM_HYPERSCORE(
			"xtandem", "hyperscore",
			"The hyperscore provided by X!Tandem for a peptide match."
		);

		private final String m_searchEngine;
		private final String m_name;
		private final String m_description;

		private Type(final String searchEngine, final String name, final String description) {
			assert (!StringUtils.isEmpty(searchEngine)) : "Scoring.Type() invalid searchEngine";
			assert (!StringUtils.isEmpty(name)) : "Scoring.Type() invalid name";
			assert (!StringUtils.isEmpty(description)) : "Scoring.Type() invalid description";

			m_searchEngine = searchEngine;
			m_name = name;
			m_description = description;
		}

		public String getSearchEngine() {
			return m_searchEngine;
		}

		public String getName() {
			return m_name;
		}
		
		public String getDescription() {
			return m_description;
		}
		
		@Override
		public String toString() {
			return m_searchEngine + ":" + m_name;
		}

	}


}
