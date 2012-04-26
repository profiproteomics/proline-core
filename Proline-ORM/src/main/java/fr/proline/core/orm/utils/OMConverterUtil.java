package fr.proline.core.orm.utils;

import java.util.Iterator;

import scala.actors.threadpool.Arrays;
import fr.proline.core.om.model.msi.IonTypes$;
import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.om.model.msi.PtmDefinition;
import fr.proline.core.om.model.msi.PtmNames;
import fr.proline.core.orm.ps.PeptidePtm;
import fr.proline.core.orm.ps.PtmEvidence;

public class OMConverterUtil {
	
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
		fr.proline.core.om.model.msi.PtmNames ptmName = new PtmNames(ptmPsORM.getSpecificity().getPtm().getShortName(), ptmPsORM.getSpecificity().getPtm().getFullName());
		
		//*************** PtmEvidences ***************// 
		//"Precursor" PtmEvidence for this Ptm
		fr.proline.core.om.model.msi.PtmEvidence precursorEvidence = null;		
		Iterator<fr.proline.core.orm.ps.PtmEvidence> evidencesIt = ptmPsORM.getSpecificity().getPtm().getEvidences().iterator();
		while(evidencesIt.hasNext()){
			fr.proline.core.orm.ps.PtmEvidence nextORMPtmEvidence = evidencesIt.next();
			if(nextORMPtmEvidence.getType().equals(PtmEvidence.Type.Precursor)){
				precursorEvidence = new fr.proline.core.om.model.msi.PtmEvidence(IonTypes$.MODULE$.Value("Precursor"), nextORMPtmEvidence.getComposition(), nextORMPtmEvidence.getMonoMass(), nextORMPtmEvidence.getAverageMass(),nextORMPtmEvidence.getIsRequired());
				break;
			}
		}
				
		//Get PtmEvidences referencing PtmSpecificity of specified PeptidePtm. Creates corresponding OM objects
		fr.proline.core.om.model.msi.PtmEvidence[] ptmEvidencesOM = new fr.proline.core.om.model.msi.PtmEvidence[ptmPsORM.getSpecificity().getEvidences().size()];
		boolean foundPrecursor = false;
		int i=0;
		for(fr.proline.core.orm.ps.PtmEvidence ptmEvid :ptmPsORM.getSpecificity().getEvidences() ){
			if(ptmEvid.getType().equals(PtmEvidence.Type.Precursor))
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
		fr.proline.core.om.model.msi.PtmDefinition ptmDefMsiOM = new PtmDefinition(ptmPsORM.getSpecificity().getId(), ptmPsORM.getSpecificity().getLocation(), 
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
