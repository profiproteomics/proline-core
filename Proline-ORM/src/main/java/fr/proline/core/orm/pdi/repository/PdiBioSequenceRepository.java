package fr.proline.core.orm.pdi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.BioSequence;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class PdiBioSequenceRepository extends JPARepository {

    private static final Logger LOG = LoggerFactory.getLogger(PdiBioSequenceRepository.class);

    private static final double EPSILON = 1e-6; // For double computations use 1e-14

    public PdiBioSequenceRepository(final EntityManager pdiEm) {
	super(pdiEm);
    }

    public BioSequence findBioSequenceForCrcAndMass(final String crc64, final double mass) {

	if (StringUtils.isEmpty(crc64)) {
	    throw new IllegalArgumentException("Invalid crc64");
	}

	BioSequence result = null;

	final TypedQuery<BioSequence> query = getEntityManager().createNamedQuery("findPdiBioSequenceForCrc",
		BioSequence.class);
	query.setParameter("crc64", crc64.toLowerCase());

	final List<BioSequence> bioSequences = query.getResultList();

	if ((bioSequences != null) && !bioSequences.isEmpty()) {

	    for (final BioSequence bs : bioSequences) {

		if (Math.abs(bs.getMass() - mass) < EPSILON) {

		    if (result == null) {
			result = bs;
		    } else {
			throw new RuntimeException(
				"There are more than one BioSequence for given crc64 and mass");
		    }

		} // End if (bs.mass == mass)

	    } // End loop for each BioSequence

	} // End if (bioSequences is not empty)

	return result;
    }

    public BioSequence findBioSequencePerAccessionAndSeqDB(String accession, Integer seqDbInstanceId) {
	TypedQuery<BioSequence> query = getEntityManager()
		.createQuery(
			"Select bs from fr.proline.core.orm.pdi.BioSequence bs, fr.proline.core.orm.pdi.ProteinIdentifier pi, fr.proline.core.orm.pdi.SequenceDbEntry se, fr.proline.core.orm.pdi.SequenceDbInstance si, fr.proline.core.orm.pdi.DbEntryProteinIdentifierMap dbepi"
				+ " where dbepi.proteinIdentifier = pi  and  dbepi.dbEntry = se and se.sequenceDbInstance = si and se.bioSequence = bs and pi.value = :acc and si.id = :seqDbInstId ",
			BioSequence.class);
	query.setParameter("acc", accession).setParameter("seqDbInstId", seqDbInstanceId);

	try {
	    return query.getSingleResult();
	} catch (NoResultException nre) {
	    LOG.info(" No BioSequence for accession {} and SequenceDbInstance {} ", accession,
		    seqDbInstanceId);
	    return null;
	} catch (NonUniqueResultException nure) {
	    LOG.warn(" More than one BioSequence for accession {} and SequenceDbInstance {} ", accession,
		    seqDbInstanceId);
	    return null;
	}

    }

}
