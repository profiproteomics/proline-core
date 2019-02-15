package fr.proline.core.orm.uds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.uds.repository.ProjectRepository;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.DatabaseTestCase;

public class UserAccountTest extends DatabaseTestCase {

	private static final Logger LOG = LoggerFactory.getLogger(UserAccountTest.class);

	@Override
	public ProlineDatabaseType getProlineDatabaseType() {
		return ProlineDatabaseType.UDS;
	}

	@Before
	public void setUp() throws Exception {
		initDatabase();
		String[] datasets = new String[] { "/dbunit/Init/uds-db.xml", "/dbunit/datasets/uds/Project_Dataset.xml" };
		loadCompositeDataSet(datasets);
	}

	@Override
	public String getPropertiesFileName() {
		return "db_uds.properties";
	}

	@Test
	public void writeUserAccount() {
		final EntityManager udsEm = getConnector().createEntityManager();

		try {
			UserAccount account = new UserAccount();
			account.setLogin("bruley");
			account.setPasswordHash("bruley");
			account.setCreationMode("manual");

			udsEm.getTransaction().begin();
			udsEm.persist(account);

			TypedQuery<UserAccount> query = udsEm.createQuery(
				"Select e from UserAccount e where e.login = :login", UserAccount.class);
			query.setParameter("login", "bruley");
			UserAccount account1 = query.getSingleResult();
			assertNotNull(account1);
			assertNotNull(account1.getPasswordHash());

			udsEm.getTransaction().commit();

		} finally {

			if (udsEm != null) {
				try {
					udsEm.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS EntityManager", exClose);
				}
			}

		}

	}

	@Test
	public void readAccount() {
		final EntityManager udsEm = getConnector().createEntityManager();

		try {
			TypedQuery<UserAccount> query = udsEm.createQuery(
				"Select e from UserAccount e where e.login = :login", UserAccount.class);
			query.setParameter("login", "joe");
			UserAccount account = query.getSingleResult();
			assertNotNull(account);
			assertNotNull(account.getPasswordHash());
			List<Project> ownedProjects = ProjectRepository.findOwnedProjects(udsEm, account.getId());
			assertEquals(ownedProjects.size(), 1);
		} finally {

			if (udsEm != null) {
				try {
					udsEm.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS EntityManager", exClose);
				}
			}

		}

	}

	@Test
	public void verifyPassword() {
		final EntityManager udsEm = getConnector().createEntityManager();

		try {
			TypedQuery<UserAccount> query = udsEm.createQuery(
				"Select e from UserAccount e where e.login = :login", UserAccount.class);
			query.setParameter("login", "joe");
			UserAccount account = query.getSingleResult();
			assertNotNull(account);
			assertNotNull(account.getPasswordHash());
			//	    LOG.debug("getPassword  MD5"+getHashFor("myPasswd","SHA-256"));
			assertEquals(getHashFor("proline_pswd", "SHA-256"), account.getPasswordHash());
			List<Project> ownedProjects = ProjectRepository.findOwnedProjects(udsEm, account.getId());
			assertEquals(ownedProjects.size(), 1);
		} finally {

			if (udsEm != null) {
				try {
					udsEm.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS EntityManager", exClose);
				}
			}

		}

	}

	private String getHashFor(String msg, String method) {
		String digest = null;
		try {
			MessageDigest md = MessageDigest.getInstance(method);
			byte[] hash = md.digest(msg.getBytes("UTF-8"));

			//converting byte array to Hexadecimal String
			StringBuilder sb = new StringBuilder(2 * hash.length);
			for (byte b : hash) {
				sb.append(String.format("%02x", b & 0xff));
			}
			digest = sb.toString();
		} catch (UnsupportedEncodingException ex) {
			LOG.error("error getMd5For", ex);
		} catch (NoSuchAlgorithmException ex) {
			LOG.error("error getMd5For", ex);
		}

		return digest;
	}

	@Test
	public void listAccounts() {
		final EntityManager udsEm = getConnector().createEntityManager();

		try {
			UserAccount user = udsEm.find(UserAccount.class, Long.valueOf(2L));
			TypedQuery<UserAccount> query = udsEm.createQuery("select u from UserAccount u order by u.id",
				UserAccount.class);
			List<UserAccount> users = query.getResultList();
			assertTrue(users.get(1) == user);
		} finally {

			if (udsEm != null) {
				try {
					udsEm.close();
				} catch (Exception exClose) {
					LOG.error("Error closing UDS EntityManager", exClose);
				}
			}

		}

	}

	@After
	public void tearDown() {
		super.tearDown();
	}

}
