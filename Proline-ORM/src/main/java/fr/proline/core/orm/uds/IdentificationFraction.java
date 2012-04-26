package fr.proline.core.orm.uds;

import java.io.Serializable;
import javax.persistence.*;

import java.util.Set;


/**
 * The persistent class for the identification_fraction database table.
 * 
 */
@Entity
@Table(name="identification_fraction")
public class IdentificationFraction implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	private Integer number;

	@Column(name="result_set_id")
	private Integer resultSetId;

	@Column(name="serialized_properties")
	private String serializedProperties;

	//uni-directional many-to-one association to Run
   @ManyToOne
	@JoinColumn(name="raw_file_name")
	private RawFile rawFile;
    
	//uni-directional many-to-one association to Run
   @ManyToOne
	@JoinColumn(name="run_id")
	private Run run;

   //bi-directional many-to-one association to Identification
    @ManyToOne
	private Identification identification;

	//bi-directional many-to-one association to FractionSummary
	@OneToMany(mappedBy="fraction")
	private Set<IdentificationFractionSummary> fractionSummaries;

    public IdentificationFraction() {
    }

	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getNumber() {
		return this.number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public Integer getResultSetId() {
		return this.resultSetId;
	}

	public void setResultSetId(Integer resultSetId) {
		this.resultSetId = resultSetId;
	}

	public String getSerializedProperties() {
		return this.serializedProperties;
	}

	public void setSerializedProperties(String serializedProperties) {
		this.serializedProperties = serializedProperties;
	}

	public Identification getIdentification() {
		return this.identification;
	}

	public void setIdentification(Identification identification) {
		this.identification = identification;
	}
	
	public Set<IdentificationFractionSummary> getFractionSummaries() {
		return this.fractionSummaries;
	}

	public void setFractionSummaries(Set<IdentificationFractionSummary> fractionSummaries) {
		this.fractionSummaries = fractionSummaries;
	}

	public RawFile getRawFile() {
		return rawFile;
	}

	public void setRawFile(RawFile rawfile) {
		this.rawFile = rawfile;
	}

	public Run getRun() {
		return run;
	}

	public void setRun(Run run) {
		this.run = run;
	}
	
}