package fr.proline.core.orm.pdi.repository;

import static fr.profi.util.MathUtils.EPSILON_LOW_PRECISION;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.BioSequence;
import fr.proline.core.orm.util.JPARepositoryUtils;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class PdiBioSequenceRepository {

    private PdiBioSequenceRepository() {
    }

    /**
     * Retrieves a BioSequence entity (assumed unique) by crc64 and mass.
     * 
     * @param crc64
     *            CRC 64 of BioSequence to retrieve (matched ignoring case), must not be empty.
     * @param mass
     *            Mass of BioSequence to retrieve (compared with <code>EPSILON_LOW_PRECISION</code>).
     * @return BioSequence entity or <code>null</code> if not found.
     */
    public static BioSequence findBioSequenceForCrcAndMass(final EntityManager pdiEm, final String crc64,
	    final double mass) {

	JPAUtils.checkEntityManager(pdiEm);

	if (StringUtils.isEmpty(crc64)) {
	    throw new IllegalArgumentException("Invalid crc64");
	}

	BioSequence result = null;

	final TypedQuery<BioSequence> query = pdiEm.createNamedQuery("findPdiBioSequenceForCrc",
		BioSequence.class);
	query.setParameter("crc64", crc64.toUpperCase());

	final List<BioSequence> bioSequences = query.getResultList();

	if ((bioSequences != null) && !bioSequences.isEmpty()) {

	    for (final BioSequence bs : bioSequences) {

		if (Math.abs(bs.getMass() - mass) < EPSILON_LOW_PRECISION) {

		    if (result == null) {
			result = bs;
		    } else {
			throw new NonUniqueResultException(
				"There are more than one BioSequence for given crc64 and mass");
		    }

		} // End if (bs.mass == mass)

	    } // End loop for each BioSequence

	} // End if (bioSequences is not empty)

	return result;
    }

    /**
     * Retrieves Pdi BioSequences by a Collection (List, Set...) of crcs.
     * 
     * @param crcs
     *            <code>Collection</code> of BioSequence's crc64s to retrieve (must not be empty and crc64s
     *            must be in <strong>upper case</strong>).
     * @return List of found BioSequences (can be empty if none found), associated ProteinIdentifier entities
     *         are fetched.
     */
    public static List<BioSequence> findBioSequencesForCrcs(final EntityManager pdiEm,
	    final Collection<String> crcs) {

	JPAUtils.checkEntityManager(pdiEm);

	return JPARepositoryUtils.executeInQueryAsBatch(
		pdiEm.createNamedQuery("findPdiBioSequencesForCrcs", BioSequence.class), "crcs", crcs);

    }

    public static BioSequence findBioSequencePerAccessionAndSeqDB(final EntityManager pdiEm,
	    final String accession, final long seqDbInstanceId) {

	JPAUtils.checkEntityManager(pdiEm);

	BioSequence result = null;

	final TypedQuery<BioSequence> query = pdiEm.createNamedQuery("findPdiBioSequenceForAccAndSeqDB",
		BioSequence.class);
	query.setParameter("acc", accession).setParameter("seqDbInstId", Long.valueOf(seqDbInstanceId));

	final List<BioSequence> bioSequences = query.getResultList();

	if ((bioSequences != null) && !bioSequences.isEmpty()) {

	    if (bioSequences.size() == 1) {
		result = bioSequences.get(0);
	    } else {
		throw new NonUniqueResultException(
			"There are more than one BioSequence for given accession and SequenceDbInstance");
	    }

	}

	return result;
    }

}
