package fr.proline.core.orm.ps;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 * The persistent class for the ptm_classification database table.
 * 
 */
@Entity
@NamedQuery(name = "findPtmClassificationForName", query = "select pc from fr.proline.core.orm.ps.PtmClassification pc"
	+ " where upper(pc.name) = :name")
@Table(name = "ptm_classification")
public class PtmClassification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private long id;

    private String name;

    public PtmClassification() {
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

}
