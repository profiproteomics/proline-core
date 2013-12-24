package fr.proline.core.orm.msi;

import static org.junit.Assert.*;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class SearchSettingsTest extends DatabaseTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(SearchSettingsTest.class);

    @Override
    public ProlineDatabaseType getProlineDatabaseType() {
	return ProlineDatabaseType.MSI;
    }

    @Before
    public void setUp() throws Exception {
	initDatabase();

	// "/fr/proline/core/orm/msi/Resultset_Dataset.xml",
	// "/fr/proline/core/orm/msi/MsiSearch_Dataset.xml"
	String[] datasets = new String[] { "/dbunit/datasets/msi-db_init_dataset.xml",
		"/dbunit/datasets/msi/Resultset_Dataset.xml", "/dbunit/datasets/msi/MsiSearch_Dataset.xml" };

	loadCompositeDataSet(datasets);
    }

    @Test
    public void readMsiSearches() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch msiSearch = msiEm.find(MsiSearch.class, Long.valueOf(1L));
	    assertNotNull(msiSearch);
	    assertEquals(msiSearch.getPeaklist().getId(), 1L);
	    Set<SearchSettingsSeqDatabaseMap> mappedDbs = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps();
	    assertEquals(mappedDbs.size(), 1);
	    SearchSettingsSeqDatabaseMap map = msiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertEquals(map.getSeqDatabase().getName(), "Swissprot");

	    MsiSearch secondMsiSearch = msiEm.find(MsiSearch.class, Long.valueOf(2L));
	    assertEquals(secondMsiSearch.getSearchSetting().getSearchSettingsSeqDatabaseMaps().size(), 0);
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @Test
    public void writeSearchSettingsSeqDatabaseMap() {
	final EntityManagerFactory emf = getConnector().getEntityManagerFactory();

	final EntityManager msiEm = emf.createEntityManager();

	try {
	    MsiSearch secondMsiSearch = msiEm.find(MsiSearch.class, Long.valueOf(2L));

	    SeqDatabase database = msiEm.find(SeqDatabase.class, Long.valueOf(1L));

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

	    MsiSearch secondMsiSearch2 = msiEm.find(MsiSearch.class, Long.valueOf(2L));
	    assertNotSame(secondMsiSearch, secondMsiSearch2);
	    Set<SearchSettingsSeqDatabaseMap> mappedDbs = secondMsiSearch2.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps();
	    assertEquals(mappedDbs.size(), 1);
	    SearchSettingsSeqDatabaseMap readedMap = secondMsiSearch.getSearchSetting()
		    .getSearchSettingsSeqDatabaseMaps().iterator().next();
	    assertEquals(readedMap.getSeqDatabase().getName(), "Swissprot");
	} finally {

	    if (msiEm != null) {
		try {
		    msiEm.close();
		} catch (Exception exClose) {
		    LOG.error("Error closing MSI EntityManager", exClose);
		}
	    }

	}

    }

    @After
    public void tearDown() {
	super.tearDown();
    }

}
