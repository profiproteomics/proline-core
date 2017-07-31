package fr.proline.core.orm.msi.repository;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.core.orm.msi.MsiSearch;
import fr.proline.core.orm.msi.ResultSet;
import fr.proline.repository.util.JPAUtils;

public class ResultSetRepository {
	
	private static Logger logger = LoggerFactory.getLogger(ResultSetRepository.class);
	
    private ResultSetRepository() {
    }
    
    public static List<Long> findChildMsiSearchIdsForResultSet(final EntityManager msiEm, final Long rsId) {

    	List<Long> childMsiSearches = new ArrayList<>();
    	JPAUtils.checkEntityManager(msiEm);

    	if ( rsId == null || rsId < 1 )
    		return childMsiSearches;
    	
    	ResultSet rs = msiEm.find(ResultSet.class, rsId);
    	if(rs == null)
    		return childMsiSearches;
    		    	
		MsiSearch msiSearch = rs.getMsiSearch();
		if(msiSearch != null)
			childMsiSearches.add(msiSearch.getId());
							   
		//search in child RS
		List<Long> rsIds = new ArrayList<>();
		rsIds.add(rsId);
		fillChildMsiSearches(msiEm, rsIds, childMsiSearches);
		return childMsiSearches;
    }
    
    private static void fillChildMsiSearches(EntityManager msiEm, List<Long> rsIds, List<Long> childMsiSearchIds){
    	if( rsIds.isEmpty()) 
    		return;
    		   
    	List<Long> childRsIds = new ArrayList<>();
    	Query query = msiEm.createNativeQuery("SELECT result_set_relation.child_result_set_id, result_set.msi_search_id FROM result_set, result_set_relation " +
		        "WHERE result_set.id = result_set_relation.child_result_set_id AND result_set_relation.parent_result_set_id IN (:rsIds)");
		query.setParameter("rsIds", rsIds);
		List<Object[]> queryResult = query.getResultList();
		
		for(Object[] nextResult : queryResult){
			
			childRsIds.add( ((BigInteger) nextResult[0]).longValue());
			if(nextResult[1] != null) {
				Long msiSearchId = ((BigInteger) nextResult[1]).longValue();
				if (!childMsiSearchIds.contains(msiSearchId))
					childMsiSearchIds.add(msiSearchId);
			}
		}

	    // If we have found child result sets					      		    
		fillChildMsiSearches(msiEm, childRsIds, childMsiSearchIds);
    }

}
