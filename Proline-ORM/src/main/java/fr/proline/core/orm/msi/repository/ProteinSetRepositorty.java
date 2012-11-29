package fr.proline.core.orm.msi.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.PeptideInstance;
import fr.proline.core.orm.util.JPARepository;

public class ProteinSetRepositorty extends JPARepository {

	public  ProteinSetRepositorty(EntityManager em) {
		super(em);	
	}
	
	public PeptideInstance findPeptideInstanceForPepMatch(int pepMatchID){
		TypedQuery<PeptideInstance> query = getEntityManager().createNamedQuery("findPepInstByPepMatch", PeptideInstance.class);
		query.setParameter("pmID", pepMatchID);
		return query.getSingleResult();
	}
	
	public PeptideInstance findPeptideInstanceForPeptide(int pepID){
		TypedQuery<PeptideInstance> query = getEntityManager().createNamedQuery("findPepInstForPeptideId", PeptideInstance.class);
		query.setParameter("pepID", pepID);
		return query.getSingleResult();
	}

}
