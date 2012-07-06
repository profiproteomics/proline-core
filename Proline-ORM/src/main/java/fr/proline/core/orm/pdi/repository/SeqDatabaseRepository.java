package fr.proline.core.orm.pdi.repository;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.pdi.SequenceDbInstance;
import fr.proline.core.orm.ps.Ptm;
import fr.proline.core.orm.utils.JPARepository;

public class SeqDatabaseRepository extends JPARepository {

	public SeqDatabaseRepository(EntityManager em) {
		super(em);		
	}

	/**
	 * Find the PDI SequenceDBInstance with specified name and fasta file path.
	 * 
	 * @param name : The name of SequenceDBInstance to search for
	 * @param filePath : Fasta File path of the searched SequenceDBInstance
	 * @return SequenceDbInstance  with specified name and fasta file path, null if none is found.
	 */
	public SequenceDbInstance findSeqDbInstanceWithNameAndFile(String name, String filePath){
		try { 
			TypedQuery<SequenceDbInstance> query = getEntityManager().createNamedQuery("findSeqDBByNameAndFile", SequenceDbInstance.class);
			query.setParameter("name", name);
			query.setParameter("filePath", filePath);
			return query.getSingleResult();
		}catch(NoResultException nre){
			return null;
		} 
	}
}
