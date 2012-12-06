package fr.proline.core.orm.pdi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class ProteinTest extends DatabaseTestCase {

    @Override
    public Database getDatabase() {
	return Database.PDI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/pdi/Proteins_Dataset.xml");
    }

    @Test
    public void readProtein() {
	ProteinIdentifier protein = getEntityManager().find(ProteinIdentifier.class, 341);
	assertThat(protein, notNullValue());
	assertThat(protein.getIsAcNumber(), is(false));
	assertThat(protein.getTaxon().getScientificName(), is("Pseudomonas sp."));
	assertThat(protein.getValue(), is("1A1D_PSESP"));
    }

    @Test
    public void readBioSequence() {
	BioSequence bioSeq = getEntityManager().find(BioSequence.class, 171);
	assertThat(bioSeq, notNullValue());
	assertThat(bioSeq.getLength(), is(338));
	assertThat(bioSeq.getProteinIdentifiers().size(), is(4));
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
