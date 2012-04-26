package fr.proline.core.orm.provider.msi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import scala.Option;
import scala.Some;
import scala.collection.Seq;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.Peptide;
import fr.proline.core.om.provider.msi.IPeptideProvider;
import fr.proline.core.om.provider.msi.IPeptideProvider$class;
import fr.proline.core.orm.ps.repository.PeptideRepository;
import fr.proline.core.orm.utils.OMComparatorUtil;
import fr.proline.core.orm.utils.OMConverterUtil;

public class ORMPeptideProvider implements IPeptideProvider {
	
	private static Map<EntityManager, ORMPeptideProvider> instances = new HashMap<EntityManager, ORMPeptideProvider>();
	private PeptideRepository pepRepo = null;
	
	private ORMPeptideProvider(EntityManager em){
		pepRepo = new PeptideRepository(em);
	}
	
	public static ORMPeptideProvider getInstance(EntityManager em){
		if(!instances.containsKey(em)){
			instances.put(em, new ORMPeptideProvider(em));
		}
		return instances.get(em);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Option<Peptide> getPeptide(int pepId) {
		return IPeptideProvider$class.getPeptide(this, pepId);		
	}

	
	@Override
	public Option<Peptide> getPeptide(String seq, LocatedPtm[] ptms) {		
		if(ptms == null)
			ptms = new LocatedPtm[0];
		
		HashSet<LocatedPtm> omPtmSet = new  HashSet<LocatedPtm>(Arrays.asList(ptms));
		
		List<fr.proline.core.orm.ps.Peptide> foundORMPeps = pepRepo.findPeptidesBySequence(seq);
		for(fr.proline.core.orm.ps.Peptide nextORMPep : foundORMPeps ){
			if(OMComparatorUtil.comparePeptidePtmSet(omPtmSet, nextORMPep.getPtms())){
				return new Some<Peptide>(OMConverterUtil.convertPeptidePsORM2OM(nextORMPep));
			}
		}
		return scala.Option.apply((Peptide)null);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Option<Peptide>[] getPeptides(Seq<Object> pepIds) {
		ArrayList<Option<Peptide>> foundOMPep = new ArrayList<Option<Peptide>>();
		for(int index=0; index < pepIds.length(); index++){
			fr.proline.core.orm.ps.Peptide psPep = pepRepo.getEntityManager().find(fr.proline.core.orm.ps.Peptide.class, pepIds.apply(index));
			if(psPep != null){
				foundOMPep.add(new Some<Peptide>(OMConverterUtil.convertPeptidePsORM2OM(psPep)));
			} else 
				foundOMPep.add(scala.Option.apply((Peptide)null));
		}

		return (Option<Peptide>[]) foundOMPep.toArray(new Option[foundOMPep.size()]); 
	}

}
