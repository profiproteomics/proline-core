package fr.proline.core.orm.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import fr.proline.core.om.model.msi.LocatedPtm;
import fr.proline.core.orm.ps.PeptidePtm;

public class OMComparatorUtil {

	/**
	 * Compare the OM <code>LocatedPtm</code> with the ORM <code>PeptidePtm</code>. Comparison is done on Ptm full Name and Location. 
	 * Modified <code>Peptide</code> is assumed to be the same.
	 *  
	 * @param omPepPtm : first peptide ptm to compare : LocatedPtm from OM  
	 * @param ormPtpPtm : second peptide to compare : PeptidePtm from PS/ORM  
	 * @return true if both represents the same modification on same location false otherwise. If both are null, true is returned
	 * 
	 */
	public static boolean comparePeptidePtm(LocatedPtm omPepPtm, PeptidePtm ormPtpPtm){
		if(omPepPtm == null && ormPtpPtm == null)
			return true;
		if(omPepPtm == null || ormPtpPtm == null)
			return false;
		
		return (omPepPtm.definition().names().fullName().equals(ormPtpPtm.getSpecificity().getPtm().getFullName())) && (omPepPtm.seqPosition() == ormPtpPtm.getSeqPosition());
	}
	
	/**
	 * Compare both set of OM <code>LocatedPtm</code> and ORM <code>PeptidePtm</code>. Comparison is done using above compare method (comparePeptidePtm=
	 * which will test if each Set contains comparable LocatedPtm ant PeptodePtm (comparison done on Ptm full Name and Location) 
	 *  
	 * @param omPepPtmSet: First Set of peptide ptm to compare (Set of LocatedPtm from OM)  
	 * @param ormPtpPtmSet : Second Set of peptide to compare (Set of PeptidePtm from PS/ORM)  
	 * @return true if both Set contains same peptides ptms (based on modifications and location) false otherwise. If both are null or empty, true is returned
	 * 
	 */
	public static boolean comparePeptidePtmSet(Set<LocatedPtm> omPepPtmSet, Set<PeptidePtm> ormPtpPtmSet){
		if( (omPepPtmSet == null || omPepPtmSet.isEmpty()) && (ormPtpPtmSet == null || ormPtpPtmSet.isEmpty()))
			return true;
		
		if((omPepPtmSet == null || omPepPtmSet.isEmpty()) || (ormPtpPtmSet == null || ormPtpPtmSet.isEmpty()))
			return false;
		
		if(ormPtpPtmSet.size() != omPepPtmSet.size())
			return false;
		
		Set<PeptidePtm> remainingORMPepPtmSet = new HashSet<PeptidePtm>(ormPtpPtmSet);		
		Iterator<LocatedPtm> omPepPtmIt=omPepPtmSet.iterator();
		boolean omPtmFound = true;
		while(omPepPtmIt.hasNext() && omPtmFound){
			LocatedPtm nextOMPepPtm = omPepPtmIt.next();
			
			Iterator<PeptidePtm> ormPepPtmIt=ormPtpPtmSet.iterator();
			omPtmFound = false;
			while(ormPepPtmIt.hasNext() && !omPtmFound){ // Go through PeptidePtm until corresponding LocatedPtm is found
				PeptidePtm nextORMPepPtm = ormPepPtmIt.next();
				if (comparePeptidePtm(nextOMPepPtm, nextORMPepPtm)){
					remainingORMPepPtmSet.remove(nextORMPepPtm); // Found : Remove from remaining list 
					omPtmFound = true;
				}
			} //End go through each PeptidePtm to found corresponding LocatedPtm
			
		}//End go through each LocatedPtm
		
		return remainingORMPepPtmSet.isEmpty();
	}
	
}
