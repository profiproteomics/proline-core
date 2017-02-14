package fr.proline.core.orm.msi.perf;

import static fr.profi.util.StringUtils.LINE_SEPARATOR;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Ignore;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.repository.DatabaseConnectorFactory;
import fr.proline.repository.DatabaseUpgrader;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.PostgresUtils;

@Ignore
public final class TestPgPeptidesPerf {

    private static final Logger LOG = LoggerFactory.getLogger(TestPgPeptidesPerf.class);

    private static final boolean DO_INSERT = true;

    private static final int PEPTIDES_COUNT = 100000;

    private static final int QUERIES_COUNT = 1;

    private static final int IN_COUNT = 50000;

    private static final long VACUUM_THRESHOLD = 60 * 1000L; // 1 minute

    private static final int LIMIT = 5000000;

    private static final int LINE_LENGTH = 256;

    private static final int BUFFER_SIZE = 2048;

    /* Private constructor (Utility class) */
    private TestPgPeptidesPerf() {
    }

    /**
     * @param args
     */
    public static void main(final String[] args) {
	final IDatabaseConnector dbConnector = DatabaseConnectorFactory.createDatabaseConnectorInstance(
		ProlineDatabaseType.MSI, "pg_msi.properties");

	DatabaseUpgrader.upgradeDatabase(dbConnector, false);

	final PeptideGenerator generator = new PeptideGenerator();

	Connection con = null;

	try {
	    con = dbConnector.getDataSource().getConnection();

	    final long currentMaxPeptideId = getMaxPeptideId(con);
	    LOG.debug("Max Peptide Id: {}", currentMaxPeptideId);

	    generator.setInitialPeptideId(currentMaxPeptideId + 1);

	    loadKnownSequences(generator, con);

	    for (int outer = 0; outer < PEPTIDES_COUNT; ++outer) {
		LOG.info("> > > Pass {}", outer);

		if (DO_INSERT) {
		    /* Build and insert random Peptides */
		    LOG.debug("Building {} random Peptides", PEPTIDES_COUNT);

		    long start = System.currentTimeMillis();

		    final List<Peptide> peptides = new ArrayList<Peptide>(PEPTIDES_COUNT);

		    for (int i = 0; i < PEPTIDES_COUNT; ++i) {
			peptides.add(generator.createPeptide());
		    }

		    long end = System.currentTimeMillis();

		    LOG.info("{} random Peptides built in {} ms", PEPTIDES_COUNT, end - start);

		    con.setAutoCommit(false);

		    insertWithCopy(peptides, con);

		    con.commit();
		    LOG.info("Insert committed");
		}

		runInQuery(generator, con);
	    }

	} catch (Exception ex) {
	    LOG.error("Main loop error", ex);
	} finally {

	    if (con != null) {
		try {
		    con.close();
		} catch (SQLException exClose) {
		    LOG.error("Error closing SQL Connection", exClose);
		}
	    }

	    dbConnector.close();
	}

    }

    private static long getMaxPeptideId(final Connection con) throws SQLException {
	long result = 0L;

	final Statement stmQuery = con.createStatement();

	final ResultSet rs = stmQuery.executeQuery("SELECT MAX(id) FROM peptide");

	if (rs.next()) {

	    final Object obj = rs.getObject(1);
	    if (obj instanceof Number) {
		result = ((Number) obj).longValue();
	    }

	}

	rs.close();

	stmQuery.close();

	return result;
    }

    private static void loadKnownSequences(final PeptideGenerator generator, final Connection con)
	    throws SQLException {
	LOG.debug("Retrieving {} first distinct sequences", LIMIT);
	final long start = System.currentTimeMillis();

	final Statement stmQuery = con.createStatement();

	final ResultSet rs = stmQuery.executeQuery("SELECT DISTINCT sequence FROM peptide LIMIT " + LIMIT);

	int sequencesCount = 0;

	while (rs.next()) {
	    final String sequence = rs.getString(1);
	    generator.addSequence(sequence);

	    ++sequencesCount;
	}

	rs.close();

	stmQuery.close();

	final long end = System.currentTimeMillis();
	LOG.info("{} sequences retrieved from MSI Peptide table in {} ms", sequencesCount, end - start);
    }

    private static long getPeptideCount(final Connection con) throws SQLException {
	long result = 0L;

	final Statement stmQuery = con.createStatement();

	final ResultSet rs = stmQuery.executeQuery("SELECT COUNT(*) FROM peptide");

	if (rs.next()) {

	    final Object obj = rs.getObject(1);
	    if (obj instanceof Number) {
		result = ((Number) obj).longValue();
	    }

	}

	rs.close();

	stmQuery.close();

	return result;
    }

    private static void insert(final List<Peptide> peptides, final Connection con) throws SQLException {
	/* Insert peptides */
	final int n = peptides.size();

	LOG.debug("Inserting {} random Peptides", n);
	final long start = System.currentTimeMillis();

	final PreparedStatement insertStm = con
		.prepareStatement("INSERT INTO peptide(id, \"sequence\", ptm_string, calculated_mass) VALUES (?, ?, ?, ?)");

	int totalUpdateCount = 0;

	for (int i = 0; i < n; ++i) {
	    final Peptide pept = peptides.get(i);

	    insertStm.setLong(1, pept.getId());
	    insertStm.setString(2, pept.getSequence());
	    insertStm.setString(3, pept.getPtmString());
	    insertStm.setDouble(4, pept.getCalculatedMass());

	    final int updateCount = insertStm.executeUpdate();

	    if (updateCount < 0) {
		LOG.warn("Classic insert UpdateCount: {}", updateCount);
	    } else {
		totalUpdateCount += updateCount;
	    }

	}

	insertStm.close();

	final long end = System.currentTimeMillis();
	LOG.info("{} random Peptides inserted in {} ms, total UpdateCount: {}", n, end - start,
		totalUpdateCount);
    }

    private static void insertWithCopy(final List<Peptide> peptides, final Connection con) throws Exception {
	/* Insert peptides with TEMP Table */
	final int n = peptides.size();

	LOG.debug("Inserting {} random Peptides with COPY into TEMP Table", n);

	long start = System.currentTimeMillis();
	final long begin = start;

	final String tempTableName = "temp_" + start;

	final Statement stmCreate = con.createStatement();
	final boolean createResult = stmCreate.execute("CREATE TEMP TABLE " + tempTableName
		+ " (LIKE peptide) ON COMMIT DROP"); // Drop temp table on transaction commit

	if (createResult) {
	    final ResultSet rs = stmCreate.getResultSet();

	    if (LOG.isDebugEnabled()) {
		LOG.debug("TEMP Table creation ResultSet :" + LINE_SEPARATOR + formatRS(rs));
	    }

	    rs.close();
	} else {

	    if (LOG.isDebugEnabled()) {
		LOG.debug("TEMP Table creation UpdateCount: {}", stmCreate.getUpdateCount());
	    }

	}

	stmCreate.close();

	long end = System.currentTimeMillis();
	LOG.info("{} Table created in {} ms, starting bulk COPY", tempTableName, end - start);

	start = System.currentTimeMillis();

	final CopyManager copyManager = PostgresUtils.getCopyManager(con);
	final CopyIn copyIn = copyManager.copyIn("COPY " + tempTableName
		+ " (id, \"sequence\", ptm_string, calculated_mass) FROM STDIN");

	int tempTableLength = 0;

	for (int i = 0; i < n; ++i) {
	    final Peptide pept = peptides.get(i);

	    final StringBuilder buff = new StringBuilder(LINE_LENGTH);

	    buff.append(pept.getId());
	    buff.append('\t'); // Default DELIMITER

	    buff.append(pept.getSequence());
	    buff.append('\t');

	    final String ptmString = pept.getPtmString();

	    if (ptmString == null) {
		buff.append("\\N"); // Default NULL
	    } else {
		buff.append(ptmString);
	    }

	    buff.append('\t');

	    buff.append(pept.getCalculatedMass());
	    buff.append('\n'); // Default Unix-style newline

	    /* PostgreSQL default UTF-8, all Java chars can be converted into UTF-8 byte stream */
	    final byte[] rawLine = buff.toString().getBytes("UTF-8");

	    final int lineLength = rawLine.length;

	    tempTableLength += lineLength;

	    copyIn.writeToCopy(rawLine, 0, lineLength);
	}

	final long copyUpdateCount = copyIn.endCopy();

	end = System.currentTimeMillis();
	LOG.info("{} rows copied in {} ms, TEMP Table length: {} bytes, inserting into real Peptide Table",
		copyUpdateCount, end - start, tempTableLength);

	start = System.currentTimeMillis();

	final Statement stmInsert = con.createStatement();
	final int updateCount = stmInsert
		.executeUpdate("INSERT INTO peptide(id, \"sequence\", ptm_string, calculated_mass)"
			+ " SELECT id, \"sequence\", ptm_string, calculated_mass FROM " + tempTableName);

	stmInsert.close();

	end = System.currentTimeMillis();
	LOG.info("Insertion done in {} ms  UpdateCount: {}", end - start, updateCount);

	LOG.info("{} Peptides inserted in {} ms", n, end - begin);
    }

    private static void runInQuery(final PeptideGenerator generator, final Connection con)
	    throws SQLException {
	LOG.debug("Building {} IN Querries with {} sequences each", QUERIES_COUNT, IN_COUNT);

	final long begin = System.currentTimeMillis();

	int nRows = 0;
	int knownSequences = 0;

	for (int outer = 0; outer < QUERIES_COUNT; ++outer) {
	    long start = System.currentTimeMillis();
	    final String inQuery = createInQuery(generator, IN_COUNT);

	    long end = System.currentTimeMillis();
	    LOG.info("Query length {} chars  created in {} ms", inQuery.length(), end - start);

	    start = System.currentTimeMillis();
	    final Statement stmQuery = con.createStatement();
	    final ResultSet rs = stmQuery.executeQuery(inQuery);

	    end = System.currentTimeMillis();
	    LOG.info("Statement executeQuery run in {} ms", end - start);

	    start = System.currentTimeMillis();

	    while (rs.next()) {
		++nRows;

		final String sequence = rs.getString(2);
		if (generator.isKnown(sequence)) {
		    ++knownSequences;
		}

	    }

	    rs.close();

	    end = System.currentTimeMillis();
	    LOG.info("Resultset parsed in {} ms", end - start);

	    stmQuery.close();
	}

	final long stop = System.currentTimeMillis();

	final long totalTime = stop - begin;
	LOG.info("Total {} IN Queries execution time: {} ms  nRows: {}  knownSequences: {}", QUERIES_COUNT,
		totalTime, nRows, knownSequences);

	if (totalTime > VACUUM_THRESHOLD) {
	    vacuumAnalyze(con);
	    LOG.info("Total Petide entries: {}", getPeptideCount(con));
	}

    }

    private static String createInQuery(final PeptideGenerator generator, final int nSequences) {
	final StringBuilder buff = new StringBuilder(BUFFER_SIZE);
	buff.append("SELECT id, \"sequence\", ptm_string FROM peptide WHERE \"sequence\" IN (");

	final Iterator<String> sequenceIterator = generator.getSequenceIterator();

	boolean first = true;

	for (int i = 0; i < nSequences; ++i) {

	    if (first) {
		first = false;
	    } else {
		buff.append(", ");
	    }

	    String sequence = null;

	    if (((i % 2) == 0) && sequenceIterator.hasNext()) {
		sequence = sequenceIterator.next(); // Real existing sequence
	    } else {
		sequence = generator.getRandomString(); // Fake sequence
	    }

	    buff.append('\'').append(sequence).append('\'');
	}

	buff.append(')');

	return buff.toString();
    }

    private static void vacuumAnalyze(final Connection con) throws SQLException {
	LOG.debug("Running vacuum analyze on peptide table");
	final long start = System.currentTimeMillis();

	con.setAutoCommit(true); // Vacuum must be run outside SQL transaction

	final Statement stmVacuum = con.createStatement();

	final boolean vacuumResult = stmVacuum.execute("VACUUM ANALYZE peptide");

	if (vacuumResult) {
	    final ResultSet rs = stmVacuum.getResultSet();

	    if (LOG.isDebugEnabled()) {
		LOG.debug("Vacuum ResultSet :" + LINE_SEPARATOR + formatRS(rs));
	    }

	    rs.close();
	} else {

	    if (LOG.isDebugEnabled()) {
		LOG.debug("Vacuum UpdateCount: {}", stmVacuum.getUpdateCount());
	    }

	}

	stmVacuum.close();

	final long end = System.currentTimeMillis();
	LOG.info("Vacuum analyze run in {} ms", end - start);
    }

    private static String formatRS(final ResultSet rs) throws SQLException {
	final StringBuilder buff = new StringBuilder(BUFFER_SIZE);

	final ResultSetMetaData rsMetaData = rs.getMetaData();
	final int nColumns = rsMetaData.getColumnCount();

	while (rs.next()) {
	    boolean first = true;

	    for (int i = 1; i <= nColumns; ++i) {

		if (first) {
		    first = false;
		} else {
		    buff.append(" | ");
		}

		buff.append(rs.getObject(i));
	    }

	    buff.append(LINE_SEPARATOR);
	}

	return buff.toString();
    }

}
