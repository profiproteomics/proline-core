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
import fr.proline.core.om.model.msi.IonTypes$;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.PeptideInstance;
import fr.proline.core.orm.msi.PeptideSetPeptideInstanceItem;
import fr.proline.core.orm.msi.repository.ProteinSetRepositorty;
import fr.proline.core.orm.ps.PeptidePtm;
import fr.proline.core.orm.utils.JPAUtil;
import fr.proline.repository.ProlineRepository;
import fr.proline.repository.ProlineRepository.Databases;

public class OMConverterUtil {
	
	
	public static fr.proline.core.om.model.msi.PeptideMatch convertPeptideMatchORM2OM(fr.proline.core.orm.msi.PeptideMatch pepMatchORM){
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
	public static fr.proline.core.om.model.msi.PeptideInstance convertPeptideInstanceORM2OM(fr.proline.core.orm.msi.PeptideInstance pepInstORM, boolean getPepMatches, EntityManager msiEM){

		//Util to access data in repositories
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
		fr.proline.core.om.model.msi.PeptideInstance[] pepInstanceChilsArray = new fr.proline.core.om.model.msi.PeptideInstance[pepInstChilds.size()];
		pepInstanceChilsArray = pepInstChilds.values().toArray( pepInstanceChilsArray);

		//Get Peptide, Unmodified Peptide && PeptideInstance 
		fr.proline.repository.DatabaseConnector psDBConnector = prolineRepo.getConnector(Databases.PS);		
		EntityManager em = Persistence.createEntityManagerFactory(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName(), psDBConnector.getEntityManagerSettings()).createEntityManager();		
		fr.proline.core.orm.ps.Peptide assocPep = em.find(fr.proline.core.orm.ps.Peptide.class, pepInstORM.getPeptideId());	
		
		fr.proline.core.orm.ps.Peptide unmodifiedPep = em.find(fr.proline.core.orm.ps.Peptide.class, pepInstORM.getUnmodifiedPeptideId() );
		Option<fr.proline.core.om.model.msi.Peptide> unmodifiedOMPep = scala.Option.apply((fr.proline.core.om.model.msi.Peptide)null);
		if(unmodifiedPep != null){
			unmodifiedOMPep =  new Some<fr.proline.core.om.model.msi.Peptide> (convertPeptidePsORM2OM(unmodifiedPep));
		}
		
		
		fr.proline.core.orm.msi.PeptideInstance unmodifiedPepInst  = proSetRepo.findPeptideInstanceForPeptide(pepInstORM.getUnmodifiedPeptideId());
		Option<fr.proline.core.om.model.msi.PeptideInstance> unmodifiedOMPepInst = scala.Option.apply((fr.proline.core.om.model.msi.PeptideInstance)null);
		if(unmodifiedPepInst!=null)
			unmodifiedOMPepInst = new Some<fr.proline.core.om.model.msi.PeptideInstance> (convertPeptideInstanceORM2OM(unmodifiedPepInst, getPepMatches, msiEM));
		
		
		
		//Get PeptideSet
		Iterator<PeptideSetPeptideInstanceItem> pepSetItemIT = pepInstORM.getPeptideSetPeptideInstanceItems().iterator();
		Map<Integer, fr.proline.core.om.model.msi.PeptideSet> pepSetsById = new HashMap<Integer, fr.proline.core.om.model.msi.PeptideSet>();
		while(pepSetItemIT.hasNext()){
			PeptideSetPeptideInstanceItem nextPSPIItem = pepSetItemIT.next();
			if(!pepSetsById.containsKey(nextPSPIItem.getPeptideSet().getId())){
				pepSetsById.put(nextPSPIItem.getPeptideSet().getId(), convertPepSetORM2OM(nextPSPIItem.getPeptideSet()));
			}			
		}
				
		fr.proline.core.om.model.msi.PeptideSet[] peptideSets = new fr.proline.core.om.model.msi.PeptideSet[pepSetsById.size()];
		peptideSets = pepSetsById.values().toArray(peptideSets);
		
		//Create OM PeptideInstance 
		fr.proline.core.om.model.msi.PeptideInstance convertedPepInst  = new fr.proline.core.om.model.msi.PeptideInstance(pepInstORM.getId(), 
									convertPeptidePsORM2OM(assocPep), pepMatchesIds, pepMatchesOM, pepInstanceChilsArray, pepInstORM.getUnmodifiedPeptideId(), unmodifiedOMPep, 
									unmodifiedPepInst.getId(), unmodifiedOMPepInst, pepInstORM.getProteinMatchCount(),
									pepInstORM.getProteinSetCount(),pepInstORM.getSelectionLevel(), peptideSets, pepInstORM.getResultSummary().getId(), null, null);
	
		return convertedPepInst;
	}
	
	
	public static fr.proline.core.om.model.msi.PeptideSet convertPepSetORM2OM(fr.proline.core.orm.msi.PeptideSet pepSetORM){
		return null;
	}
		
	public static fr.proline.core.om.model.msi.Peptide convertPeptidePsORM2OM(fr.proline.core.orm.ps.Peptide pepPsORM){
		
		// **** Create OM LocatedPtm for specified Peptide
		fr.proline.core.om.model.msi.LocatedPtm[] locatedPtms = new fr.proline.core.om.model.msi.LocatedPtm[pepPsORM.getPtms().size()]; 
		Iterator<PeptidePtm> pepPtmIt = pepPsORM.getPtms().iterator();
		int index = 0;
		while(pepPtmIt.hasNext()){
			locatedPtms[index] = convertPtmPsORM2OM(pepPtmIt.next());
			index++;
		}
		
						
		// **** Create OM Peptide
		fr.proline.core.om.model.msi.Peptide pepMsiOm =  new fr.proline.core.om.model.msi.Peptide(pepPsORM.getId(),pepPsORM.getSequence(),pepPsORM.getPtmString(), locatedPtms, pepPsORM.getCalculatedMass(), null);
		return pepMsiOm;
	}
	
	/**
	 *  Convert from fr.proline.core.orm.ps.PeptidePtm (ORM) to fr.proline.core.om.model.msi.LocatedPtm (OM).
	 *  
	 * LocatedPtm, PtmDefinition, PtmEvidence and PtmNames will be created from specified 
	 * PeptidePtm, associated PtmSpecificity, PtmEvidence and Ptm 
	 * 
	 * 
	 * @param ptmPsORM : fr.proline.core.orm.ps.PeptidePtm to convert
	 * @return created LocatedPtm (with associated objects)
	 */
	public static fr.proline.core.om.model.msi.LocatedPtm convertPtmPsORM2OM(fr.proline.core.orm.ps.PeptidePtm ptmPsORM){		
		
		//Create PtmNames from Ptm
		fr.proline.core.om.model.msi.PtmNames ptmName = new fr.proline.core.om.model.msi.PtmNames(ptmPsORM.getSpecificity().getPtm().getShortName(), ptmPsORM.getSpecificity().getPtm().getFullName());
		
		//*************** PtmEvidences ***************// 
		//"Precursor" PtmEvidence for this Ptm
		fr.proline.core.om.model.msi.PtmEvidence precursorEvidence = null;		
		Iterator<fr.proline.core.orm.ps.PtmEvidence> evidencesIt = ptmPsORM.getSpecificity().getPtm().getEvidences().iterator();
		while(evidencesIt.hasNext()){
			fr.proline.core.orm.ps.PtmEvidence nextORMPtmEvidence = evidencesIt.next();
			if(nextORMPtmEvidence.getType().equals(fr.proline.core.orm.ps.PtmEvidence.Type.Precursor)){
				precursorEvidence = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes$.MODULE$.Value("Precursor"), nextORMPtmEvidence.getComposition(), nextORMPtmEvidence.getMonoMass(), nextORMPtmEvidence.getAverageMass(),nextORMPtmEvidence.getIsRequired());
				break;
			}
		}
				
		//Get PtmEvidences referencing PtmSpecificity of specified PeptidePtm. Creates corresponding OM objects
		fr.proline.core.om.model.msi.PtmEvidence[] ptmEvidencesOM = new fr.proline.core.om.model.msi.PtmEvidence[ptmPsORM.getSpecificity().getEvidences().size()];
		boolean foundPrecursor = false;
		int i=0;
		for(fr.proline.core.orm.ps.PtmEvidence ptmEvid :ptmPsORM.getSpecificity().getEvidences() ){
			if(ptmEvid.getType().equals(fr.proline.core.orm.ps.PtmEvidence.Type.Precursor))
				foundPrecursor = true;
			ptmEvidencesOM[i] = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes$.MODULE$.Value(ptmEvid.getType().name()), ptmEvid.getComposition(), ptmEvid.getMonoMass(), ptmEvid.getAverageMass(),ptmEvid.getIsRequired());
			i++;
		}
		if(!foundPrecursor){
			fr.proline.core.om.model.msi.PtmEvidence[] newPtmEvidencesOM =(fr.proline.core.om.model.msi.PtmEvidence[] ) Arrays.copyOf(ptmEvidencesOM, ptmEvidencesOM.length+1);
			newPtmEvidencesOM[ptmEvidencesOM.length] = precursorEvidence;
		}
		
				
		// Create OM PtmDefinition from ORM PtmSpecificity
		char residue = (ptmPsORM.getSpecificity().getResidue()!=null ? ptmPsORM.getSpecificity().getResidue().charAt(0) : null);
		fr.proline.core.om.model.msi.PtmDefinition ptmDefMsiOM = new fr.proline.core.om.model.msi.PtmDefinition(ptmPsORM.getSpecificity().getId(), ptmPsORM.getSpecificity().getLocation(), 
				ptmName, ptmEvidencesOM,residue, ptmPsORM.getSpecificity().getClassification().getName(), ptmPsORM.getId());
		
		//Attributes values conversion 
		Integer seqPosition= ptmPsORM.getSeqPosition();
		boolean isNterm = seqPosition.equals(0);
		boolean isCterm = seqPosition.equals(-1);

		//Create OM LocatedPtm from ORM PeptidePtm
		fr.proline.core.om.model.msi.LocatedPtm ptmMsiOM = new LocatedPtm(ptmDefMsiOM, ptmPsORM.getSeqPosition(), ptmPsORM.getMonoMass(), ptmPsORM.getAverageMass(), precursorEvidence.composition(), isNterm, isCterm);
		return ptmMsiOM;
	}
	

	
}
