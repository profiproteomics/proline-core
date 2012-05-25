package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItems;

import java.util.Collection;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.utils.DatabaseTestCase;
import fr.proline.repository.utils.DatabaseUtils;

public class ResultsetTest extends DatabaseTestCase {

	PeptideMatchRepository pmRepo;

	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName());
        loadDataSet("/fr/proline/core/orm/msi/Resultset_Dataset.xml");
        pmRepo = new PeptideMatchRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readMsiSearch() {
		MsiSearch msiSearch = em.find(MsiSearch.class, 1);
		assertThat(msiSearch, CoreMatchers.notNullValue());
		assertThat(msiSearch.getPeaklist().getId(), is(1));
		Enzyme enzyme = em.find(Enzyme.class, 1);
		assertThat(msiSearch.getSearchSetting().getEnzymes(), hasItems(enzyme));
		msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
		assertThat(msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(1));
		SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().iterator().next();
		assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
		assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch.getSearchSetting().getInstrumentConfigId());
	}


	@Test public void readDecoyResultSet() {
		ResultSet rs = em.find(ResultSet.class, 1);
		MsiSearch msiSearch = em.find(MsiSearch.class, 1);
		assertThat(rs, CoreMatchers.notNullValue());
		assertThat(rs.getMsiSearch(), sameInstance(msiSearch));
		assertThat(rs.getChildren().isEmpty(), is(true));
		assertThat(rs.getDecoyResultSet(), CoreMatchers.nullValue());
	}

	@Test public void readResultSet() {
		ResultSet rs = em.find(ResultSet.class, 2);
		MsiSearch msiSearch = em.find(MsiSearch.class, 1);
		assertThat(rs, CoreMatchers.notNullValue());
		assertThat(rs.getMsiSearch(), is(msiSearch));
		assertThat(rs.getChildren().isEmpty(), is(true));
		assertThat(rs.getDecoyResultSet(), CoreMatchers.notNullValue());
	}

	@Test public void readResultSetHierarchy() {
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

	@Test public void testSearchInheritance() {
		MsmsSearch msmsSearch = em.find(MsmsSearch.class, 1);
		assertThat(msmsSearch, CoreMatchers.notNullValue());
		Enzyme enzyme = em.find(Enzyme.class, 1);
		assertThat(msmsSearch.getEnzymes(), hasItems(enzyme));
		assertEquals(msmsSearch.getFragmentMassErrorTolerance(), 0.8, 0.001);
	}
	
	@Test public void testReadPeptideMatches() {
		ResultSet rs = em.find(ResultSet.class, 2);
		Collection<PeptideMatch> matches = pmRepo.findPeptideMatchByResultSet(rs.getId());
		assertThat(matches.size(), is(4));
	}
	
	@Test public void peptideMatchRelations() {
		PeptideMatch match = em.find(PeptideMatch.class, 1);
		assertThat(match.getBestPeptideMatch(), CoreMatchers.nullValue());
		assertThat(match.getChildren().isEmpty(), is(true));
		MsQuery query = match.getMsQuery();
		assertThat(query, CoreMatchers.notNullValue());
		assertThat(query.getCharge(), is(match.getCharge()));
		assertThat(query.getSpectrum(), CoreMatchers.notNullValue());
	}
	
	@Test public void peptidesFromMatches() {
		PeptideMatch match = em.find(PeptideMatch.class, 1);
		Peptide peptide = em.find(Peptide.class, match.getPeptideId());
		assertThat(peptide, CoreMatchers.notNullValue());
		assertThat(peptide.getSequence(), is("VLQAELK"));

		List<PeptideMatch> matches = pmRepo.findPeptideMatchByPeptide(match.getPeptideId());
		assertThat(matches, hasItems(match));		
	}
	
	@Override
	public String getSQLScriptLocation() {
		return DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION;
	}

}
