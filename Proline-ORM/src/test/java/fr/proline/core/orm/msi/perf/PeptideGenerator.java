package fr.proline.core.orm.msi.perf;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Ignore;

import fr.proline.core.orm.msi.Peptide;
import fr.proline.util.StringUtils;

@Ignore
public class PeptideGenerator {

    /* Constants */
    private static final int MIN_SEQUENCE_LENGTH = 5;

    private static final int SEQUENCE_LENGTH = 28;

    private static final double MASS_FACTOR = 1e6;

    private static final double PTM_PROBA = 1.0 / 2; // 1.0 = NO PTM ; <= 0.0 ALL PTM

    private static final int CHAR_RANGE = 'Z' - 'A' + 1;

    /* WARN Nothing is thread-safe in this class : use PeptideGenerator localy */
    private final Random m_randomGenerator = new Random();

    private final AtomicLong m_peptideId = new AtomicLong(1L);

    private final Set<String> m_knownSequences = new HashSet<String>();

    private String m_lastSequence;

    public void setInitialPeptideId(final long peptideId) {
	m_peptideId.set(peptideId);
    }

    public boolean addSequence(final String sequence) {

	if (StringUtils.isEmpty(sequence)) {
	    throw new IllegalArgumentException("Invalid sequence");
	}

	return m_knownSequences.add(sequence);
    }

    public Peptide createPeptide() {
	final Peptide pept = new Peptide();

	pept.setId(m_peptideId.getAndIncrement());

	if (m_randomGenerator.nextDouble() < PTM_PROBA) {
	    /* This Peptide has ptmString */
	    String sequence = m_lastSequence;

	    if (sequence == null) {
		sequence = getUniqueSequence();
	    }

	    pept.setSequence(sequence);
	    pept.setPtmString(getRandomString());

	    m_lastSequence = null;
	} else {
	    /* This Peptide has no ptmString */
	    pept.setSequence(getUniqueSequence());
	    pept.setPtmString(null);
	}

	pept.setCalculatedMass(getRandomMass());

	return pept;
    }

    public String getRandomString() {
	final int length = m_randomGenerator.nextInt(SEQUENCE_LENGTH) + MIN_SEQUENCE_LENGTH;

	final StringBuilder buff = new StringBuilder(length);

	for (int i = 0; i < length; ++i) {
	    buff.append(getRandomChar());
	}

	return buff.toString();
    }

    public Iterator<String> getSequenceIterator() {
	return m_knownSequences.iterator();
    }

    public boolean isKnown(final String sequence) {
	return m_knownSequences.contains(sequence);
    }

    private String getUniqueSequence() {
	String sequence = getRandomString();

	while (m_knownSequences.contains(sequence)) {
	    sequence = getRandomString();
	}

	m_knownSequences.add(sequence);
	m_lastSequence = sequence;

	return sequence;
    }

    private double getRandomMass() {
	return (m_randomGenerator.nextDouble() * MASS_FACTOR);
    }

    private char getRandomChar() {
	return (char) (m_randomGenerator.nextInt(CHAR_RANGE) + 'A');
    }

}
