package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;

import java.util.Set;

import javax.persistence.EntityManager;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class SearchSettingsTest extends DatabaseTestCase {

    @Override
    public Database getDatabase() {
	return Database.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	loadCompositeDataSet(new String[] { "/fr/proline/core/orm/msi/Resultset_Dataset.xml",
		"/fr/proline/core/orm/msi/MsiSearch_Dataset.xml" });
    }

    @Test
    public void readMsiSearches() {
	final EntityManager em = getEntityManager();
	MsiSearch msiSearch = em.find(MsiSearch.class, 1);
	assertThat(msiSearch, CoreMatchers.notNullValue());
	assertThat(msiSearch.getPeaklist().getId(), is(1));
	Set<SearchSettingsSeqDatabaseMap> mappedDbs = msiSearch.getSearchSetting()
		.getSearchSettingsSeqDatabaseMaps();
	assertThat(mappedDbs.size(), is(1));
	SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps()
		.iterator().next();
	assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
	assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch.getSearchSetting()
		.getInstrumentConfigId());

	MsiSearch secondMsiSearch = em.find(MsiSearch.class, 2);
	assertThat(secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(0));
    }

    @Test
    public void writeSearchSettingsSeqDatabaseMap() {
	final EntityManager em = getEntityManager();
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
	Set<SearchSettingsSeqDatabaseMap> mappedDbs = secondMsiSearch2.getSearchSetting()
		.getSearchSettingsSeqDatabaseMaps();
	assertThat(mappedDbs.size(), is(1));
	SearchSettingsSeqDatabaseMap readedMap = secondMsiSearch.getSearchSetting()
		.getSearchSettingsSeqDatabaseMaps().iterator().next();
	assertThat(readedMap.getSeqDatabase().getName(), is("Swissprot"));
    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
