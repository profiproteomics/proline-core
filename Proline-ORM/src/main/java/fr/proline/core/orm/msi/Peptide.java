package fr.proline.core.orm.msi;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Transient;

import fr.proline.core.orm.utils.StringUtils;

/**
 * The persistent class for the peptide database table.
 * 
 */
@Entity(name = "fr.proline.core.orm.msi.Peptide")
@NamedQueries({
	@NamedQuery(name = "findMsiPepsForSeq", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where lower(p.sequence) = :seq"),

	@NamedQuery(name = "findMsiPepsForIds", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where p.id in :ids"),

	@NamedQuery(name = "findMsiPeptForSeqAndPtmStr", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where (lower(p.sequence) = :seq) and (lower(p.ptmString) = :ptmStr))"),

	@NamedQuery(name = "findMsiPeptForSeq", query = "select p from fr.proline.core.orm.msi.Peptide p"
		+ " where (lower(p.sequence) = :seq) and (p.ptmString is null)")

})
public class Peptide implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    // MSI Peptide Id are not generated (taken from Ps Peptide entity)
    private Integer id;

    @Column(name = "calculated_mass")
    private double calculatedMass;

    @Column(name = "ptm_string")
    private String ptmString;

    private String sequence;

    @Column(name = "serialized_properties")
    private String serializedProperties;

    // Transient Variable not saved in database
    @Transient private SequenceMatch sequenceMatch = null;
    
    public Peptide() {
    }

    /**
     * Create a Msi Peptide entity from a Ps Peptide entity. Created Msi Peptide entity shares the same Id
     * with given Ps Peptide.
     * 
     * @param psPeptide
     *            Peptide entity from psDb used to initialize Msi Peptide fields (must not be
     *            <code>null</code>)
     */
    public Peptide(final fr.proline.core.orm.ps.Peptide psPeptide) {

	if (psPeptide == null) {
	    throw new IllegalArgumentException("PsPeptide is null");
	}

	setId(psPeptide.getId());
	setCalculatedMass(psPeptide.getCalculatedMass());

	final String ptmString = psPeptide.getPtmString();

	if (StringUtils.isEmpty(ptmString)) {
	    setPtmString(null);
	} else {
	    setPtmString(ptmString);
	}

	setSequence(psPeptide.getSequence());
	setSerializedProperties(psPeptide.getSerializedProperties());
    }

    public Integer getId() {
	return this.id;
    }

    public void setId(Integer id) {
	this.id = id;
    }

    public double getCalculatedMass() {
	return this.calculatedMass;
    }

    public void setCalculatedMass(double calculatedMass) {
	this.calculatedMass = calculatedMass;
    }

    public String getPtmString() {
	return this.ptmString;
    }

    public void setPtmString(String ptmString) {
	this.ptmString = ptmString;
    }

    public String getSequence() {
	return this.sequence;
    }

    public void setSequence(String sequence) {
	this.sequence = sequence;
    }

    public String getSerializedProperties() {
	return this.serializedProperties;
    }

    public void setSerializedProperties(String serializedProperties) {
	this.serializedProperties = serializedProperties;
    }

	public SequenceMatch getTransientSequenceMatch() {
		return sequenceMatch;
	}

	public void setTransientSequenceMatch(SequenceMatch sequenceMatch) {
		this.sequenceMatch = sequenceMatch;
	}
 

	
	
}