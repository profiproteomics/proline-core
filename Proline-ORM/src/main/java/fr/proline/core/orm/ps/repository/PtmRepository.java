package fr.proline.core.orm.ps.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.ps.Ptm;
import fr.proline.core.orm.ps.PtmEvidence;
import fr.proline.core.orm.ps.PtmEvidence.Type;
import fr.proline.core.orm.ps.PtmSpecificity;
import fr.proline.core.orm.utils.JPARepository;

public class PtmRepository extends JPARepository {

	public PtmRepository(EntityManager em) {
		super(em);
	}

	public Ptm findPtmByName(String name) {
		TypedQuery<Ptm> query = getEntityManager().createNamedQuery("findPtmByName", Ptm.class);
		query.setParameter("name", name.toUpperCase());
		return query.getSingleResult();
	}
	
	public PtmEvidence findPtmEvidenceByPtmAndType(int ptmId, Type type) {
		TypedQuery<PtmEvidence> query = getEntityManager().createNamedQuery("findPtmEvidenceByPtmAndType", PtmEvidence.class);
		query.setParameter("ptm_id", ptmId);
		query.setParameter("type", type.name());
		return query.getSingleResult();
	}
	
	public PtmSpecificity findPtmSpecificityByNameLocResidu(String ptmShortName, String location, char residu) {
		TypedQuery<PtmSpecificity> query = getEntityManager().createNamedQuery("findPtmSpecificityByNameLocResidue", PtmSpecificity.class);
		query.setParameter("residu", String.valueOf(residu));
		query.setParameter("location", location);
		query.setParameter("ptmName", ptmShortName);
		return query.getSingleResult();
	}
	
	public PtmSpecificity findPtmSpecificityWithNoResiduByNameLoc( String ptmShortName, String location) {
		TypedQuery<PtmSpecificity> query = getEntityManager().createNamedQuery("findPtmSpecificityNoResiduByNameLoc", PtmSpecificity.class);		
		query.setParameter("location", location);
		query.setParameter("ptmName", ptmShortName);
		return query.getSingleResult();
	}
}
