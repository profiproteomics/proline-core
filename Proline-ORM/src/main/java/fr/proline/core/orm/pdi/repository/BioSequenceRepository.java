package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.pdi.BioSequence;
import fr.proline.core.orm.utils.JPARepository;

public class BioSequenceRepository extends JPARepository {

    private static final Logger LOG = LoggerFactory.getLogger(BioSequenceRepository.class);

    public BioSequenceRepository(EntityManager em) {
	super(em);
    }

    public BioSequence findBioSequence(String crc, String alphabet, Double mass) {
	TypedQuery<BioSequence> query = getEntityManager().createNamedQuery("findBioSequence",
		BioSequence.class);
	query.setParameter("mass", mass);
	query.setParameter("crc", crc);
	query.setParameter("alphabet", alphabet);

	try {
	    return query.getSingleResult();
	} catch (NoResultException nre) {
	    return null;
	}

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
