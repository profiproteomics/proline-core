package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.hasItems;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class ResultsetTest extends DatabaseTestCase {

    PeptideMatchRepository pmRepo;

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadDataSet("/fr/proline/core/orm/msi/Resultset_Dataset.xml");
	pmRepo = new PeptideMatchRepository(getEntityManager());
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

    @Test
    public void readMsiSearch() {
	final EntityManager em = getEntityManager();
	MsiSearch msiSearch = em.find(MsiSearch.class, 1);
	assertThat(msiSearch, CoreMatchers.notNullValue());
	assertThat(msiSearch.getPeaklist().getId(), is(1));
	Enzyme enzyme = em.find(Enzyme.class, 1);
	assertThat(msiSearch.getSearchSetting().getEnzymes(), hasItems(enzyme));
	msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
	assertThat(msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(1));
	SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps()
		.iterator().next();
	assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
	assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch.getSearchSetting()
		.getInstrumentConfigId());
    }

    @Test
    public void readDecoyResultSet() {
	final EntityManager em = getEntityManager();
	ResultSet rs = em.find(ResultSet.class, 1);
	MsiSearch msiSearch = em.find(MsiSearch.class, 1);
	assertThat(rs, CoreMatchers.notNullValue());
	assertThat(rs.getMsiSearch(), sameInstance(msiSearch));
	assertThat(rs.getChildren().isEmpty(), is(true));
	assertThat(rs.getDecoyResultSet(), CoreMatchers.nullValue());
    }

    @Test
    public void readResultSet() {
	final EntityManager em = getEntityManager();
	ResultSet rs = em.find(ResultSet.class, 2);
	MsiSearch msiSearch = em.find(MsiSearch.class, 1);
	assertThat(rs, CoreMatchers.notNullValue());
	assertThat(rs.getMsiSearch(), is(msiSearch));
	assertThat(rs.getChildren().isEmpty(), is(true));
	assertThat(rs.getDecoyResultSet(), CoreMatchers.notNullValue());
    }

    @Test
    public void readResultSetHierarchy() {
	final EntityManager em = getEntityManager();
	ResultSet rs = em.find(ResultSet.class, 3);
	assertThat(rs, CoreMatchers.notNullValue());
	assertThat(rs.getMsiSearch(), CoreMatchers.nullValue());
	assertThat(rs.getChildren().isEmpty(), is(false));
	assertThat(rs.getChildren().size(), is(2));
	ResultSet rs1 = em.find(ResultSet.class, 1);
	ResultSet rs2 = em.find(ResultSet.class, 2);
	assertThat(rs.getChildren(), hasItems(rs1, rs2));
	assertThat(rs.getChildren(), not(hasItems(rs)));
    }

    @Test
    public void testSearchInheritance() {
	final EntityManager em = getEntityManager();
	MsmsSearch msmsSearch = em.find(MsmsSearch.class, 1);
	assertThat(msmsSearch, CoreMatchers.notNullValue());
	Enzyme enzyme = em.find(Enzyme.class, 1);
	assertThat(msmsSearch.getEnzymes(), hasItems(enzyme));
	assertEquals(msmsSearch.getFragmentMassErrorTolerance(), 0.8, 0.001);
    }

    @Test
    public void testReadPeptideMatches() {
	ResultSet rs = getEntityManager().find(ResultSet.class, 2);
	Collection<PeptideMatch> matches = pmRepo.findPeptideMatchByResultSet(rs.getId());
	assertThat(matches.size(), is(4));
    }

    @Test
    public void peptideMatchRelations() {
	PeptideMatch match = getEntityManager().find(PeptideMatch.class, 1);
	assertThat(match.getBestPeptideMatch(), CoreMatchers.nullValue());
	assertThat(match.getChildren().isEmpty(), is(true));
	MsQuery query = match.getMsQuery();
	assertThat(query, CoreMatchers.notNullValue());
	assertThat(query.getCharge(), is(match.getCharge()));
	assertThat(query.getSpectrum(), CoreMatchers.notNullValue());
    }

    @Test
    public void peptidesFromMatches() {
	final EntityManager em = getEntityManager();
	PeptideMatch match = em.find(PeptideMatch.class, 1);
	Peptide peptide = em.find(Peptide.class, match.getPeptideId());
	assertThat(peptide, CoreMatchers.notNullValue());
	assertThat(peptide.getSequence(), is("VLQAELK"));

	List<PeptideMatch> matches = pmRepo.findPeptideMatchByPeptide(match.getPeptideId());
	assertThat(matches, hasItems(match));
    }

    @Override
    public Database getDatabase() {
	return Database.MSI;
    }

    @Override
    public String getSQLScriptLocation() {
	return DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION;
    }

}
