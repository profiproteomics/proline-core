package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the aggregation database table.
 * 
 */
@Entity
@NamedQueries({
	@NamedQuery(name = "findAggregationByType", query = "SELECT aggreg FROM fr.proline.core.orm.uds.Aggregation aggreg WHERE aggreg.childNature = :child_nature")
})
@Table(name = "aggregation")
public class Aggregation implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum ChildNature {
		SAMPLE_ANALYSIS, QUANTITATION_FRACTION, BIOLOGICAL_SAMPLE, BIOLOGICAL_GROUP, OTHER
	};

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "child_nature")
	@Enumerated(value = EnumType.STRING)
	private ChildNature childNature;

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public ChildNature getChildNature() {
		return childNature;
	}

	public void setChildNature(ChildNature childNature) {
		this.childNature = childNature;
	}

}
