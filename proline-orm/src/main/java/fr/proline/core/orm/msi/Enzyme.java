package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

import fr.profi.util.StringUtils;

/**
 * The persistent class for the enzyme database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.Enzyme")
@NamedQuery(name = "findMsiEnzymeForName", query = "select e from fr.proline.core.orm.msi.Enzyme e"
	+ " where upper(e.name) = :name")
public class Enzyme implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	// MSI Enzyme Id are not generated (taken from Uds Enzyme entity)
	private long id;

	@Column(name = "cleavage_regexp")
	private String cleavageRegexp;

	@Column(name = "is_independant")
	private boolean isIndependant;

	@Column(name = "is_semi_specific")
	private boolean isSemiSpecific;

	private String name;

	@Column(name = "serialized_properties")
	private String serializedProperties;

	public Enzyme() {
	}

	/**
	 * Create a Msi Enzyme entity from an Uds Enzyme entity. Created Msi Enzyme entity shares the same Id with
	 * given Uds Enzyme.
	 * 
	 * @param udsEnzyme
	 *            Enzyme entity from udsDb used to initialize Msi Enzyme fields (must not be <code>null</code>
	 *            )
	 */
	public Enzyme(final fr.proline.core.orm.uds.Enzyme udsEnzyme) {

		if (udsEnzyme == null) {
			throw new IllegalArgumentException("UdsEnzyme is null");
		}

		setId(udsEnzyme.getId());

		final String udsCleavageRegex = udsEnzyme.getCleavageRegexp();

		if (StringUtils.isEmpty(udsCleavageRegex)) {
			setCleavageRegexp(null);
		} else {
			setCleavageRegexp(udsCleavageRegex);
		}

		setIsIndependant(udsEnzyme.getIsIndependant());
		setIsSemiSpecific(udsEnzyme.getIsSemiSpecific());
		setName(udsEnzyme.getName());
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getCleavageRegexp() {
		return this.cleavageRegexp;
	}

	public void setCleavageRegexp(String cleavageRegexp) {
		this.cleavageRegexp = cleavageRegexp;
	}

	public boolean getIsIndependant() {
		return isIndependant;
	}

	public void setIsIndependant(final boolean pIsIndependant) {
		isIndependant = pIsIndependant;
	}

	public boolean getIsSemiSpecific() {
		return isSemiSpecific;
	}

	public void setIsSemiSpecific(final boolean pIsSemiSpecific) {
		isSemiSpecific = pIsSemiSpecific;
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

}
