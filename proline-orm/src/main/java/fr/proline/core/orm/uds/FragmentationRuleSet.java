package fr.proline.core.orm.uds;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Set;

/**
 * The persistent class for the fragmentation_rule_set database table.
 *
 */
@Entity
@Table(name = "fragmentation_rule_set")
public class FragmentationRuleSet implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String name;

	// uni-directional many-to-many association to FragmentationRule
	@ManyToMany
	@JoinTable(
		name = "fragmentation_rule_set_map",
		joinColumns = { @JoinColumn(name = "fragmentation_rule_set_id") },
		inverseJoinColumns = {@JoinColumn(name = "fragmentation_rule_id") }
	)
	private Set<FragmentationRule> fragmentationRules;

	public FragmentationRuleSet() {
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public Set<FragmentationRule> getFragmentationRules() {
		return this.fragmentationRules;
	}

	public void setFragmentationRules(Set<FragmentationRule> fragmentationRules) {
		this.fragmentationRules = fragmentationRules;
	}

	@Override
	public String toString() {
		return name;
	}

}
