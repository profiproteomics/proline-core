package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.msi.repository.PeptideMatchRepository;
import fr.proline.core.orm.utils.DatabaseTestCase;

public class SearchSettingsTest extends DatabaseTestCase {

	PeptideMatchRepository pmRepo;

	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager("msidb_production");
        loadCompositeDataSet(new String[] {"/fr/proline/core/orm/msi/Resultset_Dataset.xml", "/fr/proline/core/orm/msi/MsiSearch_Dataset.xml"});
        pmRepo = new PeptideMatchRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readMsiSearches() {
		MsiSearch msiSearch = em.find(MsiSearch.class, 1);
		assertThat(msiSearch, CoreMatchers.notNullValue());
		assertThat(msiSearch.getPeaklist().getId(), is(1));
		Set<SearchSettingsSeqDatabaseMap> mappedDbs = msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
		assertThat(mappedDbs.size(), is(1));
		SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().iterator().next();
		assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
		assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch.getSearchSetting().getInstrumentConfigId());

		MsiSearch secondMsiSearch = em.find(MsiSearch.class, 2);
		assertThat(secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(0));		
	}

	@Test public void writeSearchSettingsSeqDatabaseMap() {
		MsiSearch msiSearch = em.find(MsiSearch.class, 1);		
		MsiSearch secondMsiSearch = em.find(MsiSearch.class, 2);
		
		SeqDatabase database = em.find(SeqDatabase.class, 1);
		
		SearchSettingsSeqDatabaseMap map = new SearchSettingsSeqDatabaseMap();
		map.setSearchSetting(secondMsiSearch.getSearchSetting());
		map.setSeqDatabase(database);
		map.setSearchedSequencesCount(8596);
		secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().add(map);
		database.getSearchSettingsSeqDatabaseMaps().add(map);
		
		em.getTransaction().begin();
		em.persist(map);
		em.getTransaction().commit();
		
		em.clear();
		
		MsiSearch secondMsiSearch2 = em.find(MsiSearch.class, 2);
		assertThat(secondMsiSearch, not(sameInstance(secondMsiSearch2)));
		Set<SearchSettingsSeqDatabaseMap> mappedDbs = secondMsiSearch2.getSearchSetting().getSearchSettingsSeqDatabaseMaps();
		assertThat(mappedDbs.size(), is(1));
		SearchSettingsSeqDatabaseMap readedMap = secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().iterator().next();
		assertThat(readedMap.getSeqDatabase().getName(), is("Swissprot"));
	}

	
	@Override
	public String getSQLScriptLocation() {
		return "/dbscripts/msi/h2";
	}

}
