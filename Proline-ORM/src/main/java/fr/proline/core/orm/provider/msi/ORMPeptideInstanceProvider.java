package fr.proline.core.orm.provider.msi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;

import scala.Option;
import scala.collection.Seq;

import fr.proline.core.om.model.msi.PeptideInstance;
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider;
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider$class;
import fr.proline.core.orm.ps.repository.PeptideRepository;

public class ORMPeptideInstanceProvider implements IPeptideInstanceProvider {
	
	private static Map<EntityManager, ORMPeptideInstanceProvider> instances = new HashMap<EntityManager, ORMPeptideInstanceProvider>();
	private PeptideRepository pepRepo = null;
	
	private ORMPeptideInstanceProvider(EntityManager em){
		pepRepo = new PeptideRepository(em);
	}
	
	public static ORMPeptideInstanceProvider getInstance(EntityManager em){
		if(!instances.containsKey(em)){
			instances.put(em, new ORMPeptideInstanceProvider(em));
		}
		return instances.get(em);
	}
	
	@Override
	public Option<PeptideInstance> getPeptideInstance(int pepInstId) {
		return IPeptideInstanceProvider$class.getPeptideInstance(this, pepInstId);				
	}
	
	@Override
	public Option<PeptideInstance>[] getPeptideInstances(Seq<Object> pepInstIds) {
		ArrayList<Option<PeptideInstance>> foundPepInst = new ArrayList<Option<PeptideInstance>>();
		for(int index=0; index<pepInstIds.length();index++){
//			fr.proline.core.orm.ps.
		}
		return null;
	}
	@Override
	public Option<PeptideInstance>[] getResultSummariesPeptideInstances(
			Seq<Object> arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public Option<PeptideInstance>[] getResultSummaryPeptideInstances(int arg0) {
		// TODO Auto-generated method stub
		return null;
	}


}
