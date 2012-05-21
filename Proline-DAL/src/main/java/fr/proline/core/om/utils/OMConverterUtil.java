package fr.proline.core.om.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Persistence;

import scala.Option;
import scala.Some;
import scala.actors.threadpool.Arrays;
import fr.proline.core.om.model.msi.IonTypes;
import fr.proline.core.om.model.msi.IonTypes$;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.PeptideInstance;
import fr.proline.core.orm.msi.PeptideSetPeptideInstanceItem;
import fr.proline.core.orm.msi.repository.ProteinSetRepositorty;
import fr.proline.core.orm.ps.PeptidePtm;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.core.om.model.msi.PeptideSetItem;
import fr.proline.repository.ProlineRepository;
import fr.proline.repository.ProlineRepository.Databases;

/**
 * Provides method to convert object from ORM to OM. If specified in constructor, created object are stored in map( referenced by their ID) to be retrieve if necessary.
 * 
 * @author VD225637
 *
 */ 
public class OMConverterUtil {
	
	private boolean useCachedObject;
	
	Map<Integer, fr.proline.core.om.model.msi.PeptideInstance> peptideInstancesCache;
	Map<Integer, fr.proline.core.om.model.msi.PeptideMatch> peptideMatchesCache;
	Map<Integer, fr.proline.core.om.model.msi.Peptide> peptidesCache;
	Map<Integer, fr.proline.core.om.model.msi.LocatedPtm> locatedPTMSCache;
	Map<String, fr.proline.core.om.model.msi.PtmNames> ptmNamesCache;
	Map<Integer, fr.proline.core.om.model.msi.PtmDefinition> ptmDefinitionsCache;
	Map<Integer, fr.proline.core.om.model.msi.PeptideSet> peptideSetsCache;	

	
	public OMConverterUtil(){
		this(true);
	}
	
	private OMConverterUtil(boolean useCache){
		useCachedObject = useCache;
		peptideInstancesCache =  new HashMap<Integer, fr.proline.core.om.model.msi.PeptideInstance>();
		peptideMatchesCache = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideMatch>();
		peptidesCache = new HashMap<Integer, fr.proline.core.om.model.msi.Peptide>();
		locatedPTMSCache= new HashMap<Integer, fr.proline.core.om.model.msi.LocatedPtm>();
		ptmNamesCache = new HashMap<String, fr.proline.core.om.model.msi.PtmNames>();
		ptmDefinitionsCache= new HashMap<Integer, fr.proline.core.om.model.msi.PtmDefinition>();
		peptideSetsCache = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideSet>();
	}
	
	
	public fr.proline.core.om.model.msi.PeptideMatch convertPeptideMatchORM2OM(fr.proline.core.orm.msi.PeptideMatch pepMatchORM){

		//Verify if object is in cache 
		if(useCachedObject &&peptideMatchesCache.containsKey(pepMatchORM.getId()) ){
			return peptideMatchesCache.get(pepMatchORM.getId());
		}
		
		return null;
	}
	
	/**
	 * Create a OM PeptideInstance corresponding to the specified ORM PeptideInstance. 
	 * 
	 * @param pepInstORM  ORM PeptideInstance to create the OM PeptodeInstance for
	 * @param getPepMatches specify if associated OM PeptideMarch should be created or not.
	 * @param msiEM EntityManager to the MSIdb the data are issued from
	 * @return an OM PeptideInstance corresponding to specified ORM PeptideInstance. 
	 */
	public fr.proline.core.om.model.msi.PeptideInstance convertPeptideInstanceORM2OM(fr.proline.core.orm.msi.PeptideInstance pepInstORM, boolean getPepMatches, EntityManager msiEM){

		//Verify if object is in cache 
		if(useCachedObject &&peptideInstancesCache.containsKey(pepInstORM.getId()) ){
			return peptideInstancesCache.get(pepInstORM.getId());
		}
		
		//Objects to access data in repositories
		ProteinSetRepositorty  proSetRepo = new ProteinSetRepositorty(msiEM); 
		ProlineRepository prolineRepo = ProlineRepository.getRepositoryManager(null);
		
		
		//Found PeptideInstance Children mapped by their id
		Map<Integer, fr.proline.core.om.model.msi.PeptideInstance> pepInstChilds = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideInstance>();
		
		
		//---- Peptide Matches Arrays 
		Set<fr.proline.core.orm.msi.PeptideMatch> pepMatchesORMSet = pepInstORM.getPeptidesMatches(); //ORM PeptideMatches
		 
		fr.proline.core.om.model.msi.PeptideMatch[] pepMatchesOM = null; //OM PeptideMatches. Get only if asked for 
		if(getPepMatches)
			pepMatchesOM = new fr.proline.core.om.model.msi.PeptideMatch[pepMatchesORMSet.size()];
		int[] pepMatchesIds = new int[pepMatchesORMSet.size()]; //OM PeptideMatches ids
		
		//**** Create PeptideMatches array and PeptideInstance Children
		Iterator<fr.proline.core.orm.msi.PeptideMatch> pepMatchIT =pepMatchesORMSet.iterator();
		int index =0;
		while(pepMatchIT.hasNext()){
			fr.proline.core.orm.msi.PeptideMatch nextPM = pepMatchIT.next();
			
			//Go through PeptideMatch children and get associated PeptideInstance 
			Iterator<fr.proline.core.orm.msi.PeptideMatch> childsPepMatchIT = nextPM.getChildren().iterator();
			while(childsPepMatchIT.hasNext()){
				fr.proline.core.orm.msi.PeptideMatch nextChild =childsPepMatchIT.next();
				fr.proline.core.orm.msi.PeptideInstance pepInstChild = proSetRepo.findPeptideInstanceForPepMatch(nextChild.getId());
				
				if(!pepInstChilds.containsKey(pepInstChild.getId())){
					//Convert child ORM Peptide Instance to OM Peptide Instance
					pepInstChilds.put(pepInstChild.getId(), convertPeptideInstanceORM2OM(pepInstChild, getPepMatches, msiEM));
				}
			}
			
			//Fill Peptide Matches Arrays
			pepMatchesIds[index] = nextPM.getId();
			if(getPepMatches)
				pepMatchesOM[index] = convertPeptideMatchORM2OM(nextPM);
			index++;					
		}
		
		//Create Peptide Instance Child Arrays
		fr.proline.core.om.model.msi.PeptideInstance[] pepInstanceChilsArray = new fr.proline.core.om.model.msi.PeptideInstance[pepInstChilds.size()];
		pepInstanceChilsArray = pepInstChilds.values().toArray( pepInstanceChilsArray);

		//Get Peptide, Unmodified Peptide && PeptideInstance 
		fr.proline.repository.DatabaseConnector psDBConnector = prolineRepo.getConnector(Databases.PS);		
		EntityManager em = Persistence.createEntityManagerFactory(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName(), psDBConnector.getEntityManagerSettings()).createEntityManager();		
		fr.proline.core.orm.ps.Peptide assocPep = em.find(fr.proline.core.orm.ps.Peptide.class, pepInstORM.getPeptideId());	
		
		fr.proline.core.orm.ps.Peptide unmodifiedORMPep = em.find(fr.proline.core.orm.ps.Peptide.class, pepInstORM.getUnmodifiedPeptideId() );
		Option<fr.proline.core.om.model.msi.Peptide> unmodifiedOMPep = scala.Option.apply((fr.proline.core.om.model.msi.Peptide)null);
		if(unmodifiedORMPep != null){
			unmodifiedOMPep =  new Some<fr.proline.core.om.model.msi.Peptide> (convertPeptidePsORM2OM(unmodifiedORMPep));
		}
		
		
		fr.proline.core.orm.msi.PeptideInstance unmodifiedORMPepInst  = proSetRepo.findPeptideInstanceForPeptide(pepInstORM.getUnmodifiedPeptideId());
		Option<fr.proline.core.om.model.msi.PeptideInstance> unmodifiedOMPepInst = scala.Option.apply((fr.proline.core.om.model.msi.PeptideInstance)null);
		if(unmodifiedORMPepInst!=null)
			unmodifiedOMPepInst = new Some<fr.proline.core.om.model.msi.PeptideInstance> (convertPeptideInstanceORM2OM(unmodifiedORMPepInst, getPepMatches, msiEM));
		
		//Create OM PeptideInstance 
		fr.proline.core.om.model.msi.PeptideInstance convertedPepInst  = new fr.proline.core.om.model.msi.PeptideInstance(pepInstORM.getId(), 
									convertPeptidePsORM2OM(assocPep), pepMatchesIds, pepMatchesOM, pepInstanceChilsArray, pepInstORM.getUnmodifiedPeptideId(), unmodifiedOMPep, 
									unmodifiedORMPepInst.getId(), unmodifiedOMPepInst, pepInstORM.getProteinMatchCount(),
									pepInstORM.getProteinSetCount(),pepInstORM.getSelectionLevel(), pepInstORM.getElutionTime().floatValue(), null, pepInstORM.getResultSummary().getId(), null, null);
		if(useCachedObject)
			peptideInstancesCache.put(pepInstORM.getId(), convertedPepInst);
		
		//*** Create PeptideSets for current PeptideInstance 		
		Map<Integer, fr.proline.core.om.model.msi.PeptideInstance> pepInstancesById = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideInstance>();
		
		Iterator<PeptideSetPeptideInstanceItem> pepSetItemIT = pepInstORM.getPeptideSetPeptideInstanceItems().iterator();
		Map<Integer, fr.proline.core.om.model.msi.PeptideSet> pepSetsById = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideSet>();
		while(pepSetItemIT.hasNext()){
			PeptideSetPeptideInstanceItem nextPSPIItem = pepSetItemIT.next();
			if(!pepSetsById.containsKey(nextPSPIItem.getPeptideSet().getId())){ //VDS: Should not happen : PeptideInstance only once in each PeptideSet ! 
				pepSetsById.put(nextPSPIItem.getPeptideSet().getId(), convertPepSetORM2OM(nextPSPIItem.getPeptideSet(), getPepMatches, msiEM));
			}
		}
				
		fr.proline.core.om.model.msi.PeptideSet[] peptideSets = new fr.proline.core.om.model.msi.PeptideSet[pepSetsById.size()];
		peptideSets = pepSetsById.values().toArray(peptideSets);
		convertedPepInst.peptideSets_$eq(peptideSets);
	
		return convertedPepInst;
	}
	
	
	/**
	 *  Create OM PeptideSet from ORM PeptideSet and associated objects : 
	 *  - PeptideSetItem and PeptideInstance (with PeptideMatch or not depending on getPepMatchForNewPepInst value)
	 *   
	 * @param pepSetORM
	 * @return
	 */
	public fr.proline.core.om.model.msi.PeptideSet convertPepSetORM2OM(fr.proline.core.orm.msi.PeptideSet pepSetORM, boolean getPepMatchForNewPepInst, EntityManager msiEM){
		
		//Verify if exist in cache
		if(useCachedObject && peptideSetsCache.containsKey(pepSetORM.getId()))
			return peptideSetsCache.get(pepSetORM.getId());
		
		
		int[] protMatchesIds = new int[pepSetORM.getProteinMatches().size()];
		Iterator<fr.proline.core.orm.msi.ProteinMatch> protMatchesIT =  pepSetORM.getProteinMatches().iterator();
		int index = 0;
		while(protMatchesIT.hasNext()){
			protMatchesIds[index] = protMatchesIT.next().getId();
			index++;
		}
				  
		fr.proline.core.om.model.msi.PeptideSet pepSet = new fr.proline.core.om.model.msi.PeptideSet(pepSetORM.getId(), null, pepSetORM.getIsSubset() ,pepSetORM.getPeptideMatchCount() , protMatchesIds, pepSetORM.getProteinSet().getId(),
				 null, pepSetORM.getResultSummaryId(), null,  null, null,  null,  null);
		
		Option<fr.proline.core.om.model.msi.PeptideSet> pepSetOp  = new Some<fr.proline.core.om.model.msi.PeptideSet>(pepSet);
		
		
		Set<PeptideSetPeptideInstanceItem> pepSetPepItemSet = pepSetORM.getPeptideSetPeptideInstanceItems();
		Iterator<PeptideSetPeptideInstanceItem> pepSetPepItemSetIT = pepSetPepItemSet.iterator();
		PeptideSetItem[] pepSetItems = new PeptideSetItem[pepSetPepItemSet.size()];
		index = 0;
		while(pepSetPepItemSetIT.hasNext()){
			PeptideSetPeptideInstanceItem nextPSPI = pepSetPepItemSetIT.next();			
			Option<Object> isBestPepSetOp =  new Some<Object> (nextPSPI.getIsBestPeptideSet());
			//
			PeptideSetItem pepSetItem = new PeptideSetItem(nextPSPI.getSelectionLevel(), convertPeptideInstanceORM2OM(nextPSPI.getPeptideInstance(),getPepMatchForNewPepInst,msiEM), pepSetORM.getId(), pepSetOp, isBestPepSetOp, pepSetORM.getResultSummaryId(), null);
			pepSetItems[index] = pepSetItem;
			index++;
		}
		pepSet.items_$eq(pepSetItems);
		
		if(useCachedObject)
			peptideSetsCache.put(pepSet.id(),pepSet);
		return pepSet;
	}

	public fr.proline.core.om.model.msi.Peptide convertPeptidePsORM2OM(fr.proline.core.orm.ps.Peptide pepPsORM){
		
		//Verify if object is in cache 
		if(useCachedObject && peptidesCache.containsKey(pepPsORM.getId()) ){
			return peptidesCache.get(pepPsORM.getId());
		}
		
		// **** Create OM LocatedPtm for specified Peptide
		fr.proline.core.om.model.msi.LocatedPtm[] locatedPtms = new fr.proline.core.om.model.msi.LocatedPtm[pepPsORM.getPtms().size()]; 
		Iterator<PeptidePtm> pepPtmIt = pepPsORM.getPtms().iterator();
		int index = 0;
		while(pepPtmIt.hasNext()){
			locatedPtms[index] = convertPeptidePtmPsORM2OM(pepPtmIt.next());
			index++;
		}
		
						
		// **** Create OM Peptide
		fr.proline.core.om.model.msi.Peptide pepMsiOm =  new fr.proline.core.om.model.msi.Peptide(pepPsORM.getId(),pepPsORM.getSequence(),pepPsORM.getPtmString(), locatedPtms, pepPsORM.getCalculatedMass(), null);
		if(useCachedObject)
			peptidesCache.put(pepMsiOm.id(), pepMsiOm);
		return pepMsiOm;
	}
	
	/**
	 *  Convert from fr.proline.core.orm.ps.PeptidePtm (ORM) to fr.proline.core.om.model.msi.LocatedPtm (OM).
	 *  
	 * LocatedPtm, PtmDefinition, PtmEvidence and PtmNames will be created from specified 
	 * PeptidePtm and associated PtmSpecificity, PtmEvidence and Ptm 
	 * 
	 * 
	 * @param ptmPsORM : fr.proline.core.orm.ps.PeptidePtm to convert
	 * @return created LocatedPtm (with associated objects)
	 */
	public fr.proline.core.om.model.msi.LocatedPtm convertPeptidePtmPsORM2OM(fr.proline.core.orm.ps.PeptidePtm ptmPsORM){		
		
		//Verify if object is in cache 
		if(useCachedObject && locatedPTMSCache.containsKey(ptmPsORM.getId()) ){
			return locatedPTMSCache.get(ptmPsORM.getId());
		}
		
		fr.proline.core.om.model.msi.PtmEvidence precursorEvidence = null;		
		Iterator<fr.proline.core.orm.ps.PtmEvidence> evidencesIt = ptmPsORM.getSpecificity().getPtm().getEvidences().iterator();
		while(evidencesIt.hasNext()){
			fr.proline.core.orm.ps.PtmEvidence nextORMPtmEvidence = evidencesIt.next();
			if(nextORMPtmEvidence.getType().equals(fr.proline.core.orm.ps.PtmEvidence.Type.Precursor)){
				precursorEvidence = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes.Precursor(), nextORMPtmEvidence.getComposition(), nextORMPtmEvidence.getMonoMass(), nextORMPtmEvidence.getAverageMass(),nextORMPtmEvidence.getIsRequired());
				break;
			}
		}
		
		// Create OM PtmDefinition from ORM PtmSpecificity
		fr.proline.core.om.model.msi.PtmDefinition ptmDefMsiOM = convertPtmSpecificityORM2OM( ptmPsORM.getSpecificity());
		
		//Attributes values conversion 
		Integer seqPosition= ptmPsORM.getSeqPosition();
		boolean isNterm = seqPosition.equals(0);
		boolean isCterm = seqPosition.equals(-1);

		//Create OM LocatedPtm from ORM PeptidePtm
		fr.proline.core.om.model.msi.LocatedPtm ptmMsiOM = new LocatedPtm(ptmDefMsiOM, ptmPsORM.getSeqPosition(), ptmPsORM.getMonoMass(), ptmPsORM.getAverageMass(), precursorEvidence.composition(), isNterm, isCterm);
		if(useCachedObject)
			locatedPTMSCache.put(ptmPsORM.getId(), ptmMsiOM);
		return ptmMsiOM;
	}
	
	/**
	 *  Convert from fr.proline.core.orm.ps.PeptideSpecificity(ORM) to fr.proline.core.om.model.msi.PtmDefinition (OM).
	 *  
	 * 
	 * @param ptmSpecificityORM : fr.proline.core.orm.ps.PeptideSpecificity to convert
	 * @return created PtmDefinition (with associated objects)
	 */
	public fr.proline.core.om.model.msi.PtmDefinition convertPtmSpecificityORM2OM(fr.proline.core.orm.ps.PtmSpecificity ptmSpecificityORM){

		//Verify PtmDefinition exist in cache
		if(useCachedObject && ptmDefinitionsCache.containsKey(ptmSpecificityORM.getId()))
			return ptmDefinitionsCache.get(ptmSpecificityORM.getId());
				
		//*********** Create PtmNames from Ptm
		fr.proline.core.om.model.msi.PtmNames ptmName = null;
		if(useCachedObject)
			ptmName = ptmNamesCache.get(ptmSpecificityORM.getPtm().getFullName());
		if(ptmName == null){
			ptmName = new fr.proline.core.om.model.msi.PtmNames(ptmSpecificityORM.getPtm().getShortName(), ptmSpecificityORM.getPtm().getFullName());
			if(useCachedObject)
				ptmNamesCache.put(ptmSpecificityORM.getPtm().getFullName(), ptmName);
		}
		
		//*************** PtmEvidences ***************// 		
		
		//Get PtmEvidences referencing PtmSpecificity of specified PeptidePtm. Creates corresponding OM objects
		fr.proline.core.om.model.msi.PtmEvidence[] ptmEvidencesOM = new fr.proline.core.om.model.msi.PtmEvidence[ptmSpecificityORM.getEvidences().size()];
		boolean foundPrecursor = false;
		int i=0;
		for(fr.proline.core.orm.ps.PtmEvidence ptmEvid :ptmSpecificityORM.getEvidences() ){
			if(ptmEvid.getType().equals(fr.proline.core.orm.ps.PtmEvidence.Type.Precursor))
				foundPrecursor = true;
			ptmEvidencesOM[i] = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes.withName(ptmEvid.getType().name()), ptmEvid.getComposition(), ptmEvid.getMonoMass(), ptmEvid.getAverageMass(),ptmEvid.getIsRequired());
			i++;
		}
		if(!foundPrecursor){
			ptmEvidencesOM =(fr.proline.core.om.model.msi.PtmEvidence[] ) Arrays.copyOf(ptmEvidencesOM, ptmEvidencesOM.length+1);
			
			//"Precursor" PtmEvidence for this Ptm
			fr.proline.core.om.model.msi.PtmEvidence precursorEvidence = null;		
			Iterator<fr.proline.core.orm.ps.PtmEvidence> evidencesIt = ptmSpecificityORM.getPtm().getEvidences().iterator();
			while(evidencesIt.hasNext()){
				fr.proline.core.orm.ps.PtmEvidence nextORMPtmEvidence = evidencesIt.next();
				if(nextORMPtmEvidence.getType().equals(fr.proline.core.orm.ps.PtmEvidence.Type.Precursor)){
					precursorEvidence = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes.Precursor(), nextORMPtmEvidence.getComposition(), nextORMPtmEvidence.getMonoMass(), nextORMPtmEvidence.getAverageMass(),nextORMPtmEvidence.getIsRequired());
					break;
				}
			}
			
			ptmEvidencesOM[ptmEvidencesOM.length-1] = precursorEvidence;
		}
		
				
		// Create OM PtmDefinition from ORM PtmSpecificity
		char residue = (ptmSpecificityORM.getResidue()!=null ? ptmSpecificityORM.getResidue().charAt(0) : null);
		fr.proline.core.om.model.msi.PtmDefinition ptmDefMsiOM = new fr.proline.core.om.model.msi.PtmDefinition(ptmSpecificityORM.getId(), ptmSpecificityORM.getLocation(), 
						ptmName, ptmEvidencesOM, residue, ptmSpecificityORM.getClassification().getName(), ptmSpecificityORM.getPtm().getId());
		if(useCachedObject)
			ptmDefinitionsCache.put(ptmSpecificityORM.getId(), ptmDefMsiOM);
		
		return ptmDefMsiOM;
	}
	
	
}
