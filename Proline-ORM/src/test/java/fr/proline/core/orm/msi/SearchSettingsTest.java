package fr.proline.core.orm.msi;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.*;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.Database;
import fr.proline.repository.utils.DatabaseTestCase;

public class SearchSettingsTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(SearchSettingsTest.class);

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
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, 1);
	    assertThat(msiSearch, CoreMatchers.notNullValue());
	    assertThat(msiSearch.getPeaklist().getId(), is(1));
	    Set<SearchSettingsSeqDatabaseMap> mappedDbs = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps();
	    assertThat(mappedDbs.size(), is(1));
	    SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertThat(map.getSeqDatabase().getName(), is("Swissprot"));
	    assertEquals(msiSearch.getSearchSetting().getInstrumentConfig().getId(), msiSearch
		    .getSearchSetting().getInstrumentConfigId());

	    MsiSearch secondMsiSearch = msiEm.find(MsiSearch.class, 2);
	    assertThat(secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), is(0));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", msiEm);
		}
	    }

	}

    }

    @Test
    public void writeSearchSettingsSeqDatabaseMap() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, 1);
	    MsiSearch secondMsiSearch = msiEm.find(MsiSearch.class, 2);

	    SeqDatabase database = msiEm.find(SeqDatabase.class, 1);

	    SearchSettingsSeqDatabaseMap map = new SearchSettingsSeqDatabaseMap();
	    map.setSearchSetting(secondMsiSearch.getSearchSetting());
	    map.setSeqDatabase(database);
	    map.setSearchedSequencesCount(8596);
	    secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().add(map);
	    database.getSearchSettingsSeqDatabaseMaps().add(map);

	    msiEm.getTransaction().begin();
	    msiEm.persist(map);
	    msiEm.getTransaction().commit();

	    msiEm.clear();

	    MsiSearch secondMsiSearch2 = msiEm.find(MsiSearch.class, 2);
	    assertThat(secondMsiSearch, not(sameInstance(secondMsiSearch2)));
	    Set<SearchSettingsSeqDatabaseMap> mappedDbs = secondMsiSearch2.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps();
	    assertThat(mappedDbs.size(), is(1));
	    SearchSettingsSeqDatabaseMap readedMap = secondMsiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertThat(readedMap.getSeqDatabase().getName(), is("Swissprot"));
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", msiEm);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
