package fr.proline.repository.migration.msi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration;


public class V0_6__RS_Hierarchy_Update implements JdbcMigration {

	private static final Logger LOG = LoggerFactory.getLogger(V0_6__RS_Hierarchy_Update.class);
	
	/**
	 * Migrate MSI db in order to 
	 * - set value of result_set.merged_rsm_id when necessary (result set created from merged RSM)
	 * - set value of result_set_relation even on result_summary merge  		
	 * 
	 * Go through RSM hierarchie ans duplicate it on RS side: If RSM hierarchy exist then RS should not exist !
	 * 
	 * RS referenced by merged RSM should not already be in RS hierarchy: OK in current Proline  
	 *  
	 * 
	 */
	@Override
	public void migrate(Connection msiConn) throws Exception {
		
		Statement statement = msiConn.createStatement();
		
		//Statement used to insert ResultSet Relation
		PreparedStatement insertRSRelationStmnt = msiConn.prepareStatement("INSERT INTO result_set_relation (parent_result_set_id,child_result_set_id) VALUES (? , ?)");
		//Statement used to fill rs.megedRSM field.
		PreparedStatement insertRSMergedRSMStmnt = msiConn.prepareStatement("UPDATE result_set set merged_rsm_id = ?  WHERE id = ?");
		
		PreparedStatement testIfRSRelationExistStmnt =  msiConn.prepareStatement("SELECT parent_result_set_id, child_result_set_id from result_set_relation WHERE parent_result_set_id = ? AND child_result_set_id = ?");
		
		//** Get information on RSM hierarchy and associated RS
		HashMap<Long, ArrayList<Long>> rsmChildsByParents = new HashMap<Long, ArrayList<Long>>();
		HashMap<Long, Long> rsIdByRsmId = new HashMap<Long, Long>();
		
        try {

			ResultSet rs = statement.executeQuery("SELECT par.result_set_id as parent_result_set_id, par.id as rsmPId, "
					+ "  child.result_set_id as child_result_set_id, child.id as rsmCId "
					+ "FROM  public.result_summary as par,  public.result_summary as child,  public.result_summary_relation "
					+ "WHERE  result_summary_relation.parent_result_summary_id = par.id AND result_summary_relation.child_result_summary_id = child.id;");
			
			while( rs.next()){
				Long rsmParentId = rs.getLong("rsmPId");
				Long rsmChildId = rs.getLong("rsmCId");				
				Long rsParentId = rs.getLong("parent_result_set_id");
				Long rsChildId = rs.getLong("child_result_set_id");

				if(rsmChildsByParents.containsKey(rsmParentId)){
					rsmChildsByParents.get(rsmParentId).add(rsmChildId);
				} else {
					ArrayList<Long> childs = new ArrayList<Long>();
					childs.add(rsmChildId);
					rsmChildsByParents.put(rsmParentId, childs);
				}

				rsIdByRsmId.put(rsmChildId, rsChildId);
				rsIdByRsmId.put(rsmParentId, rsParentId);
			}
			
			//Go through RSM hierarchy and duplicate it on RS side
			Iterator<Long> rsmParentIdsIt = rsmChildsByParents.keySet().iterator();
			while(rsmParentIdsIt.hasNext()){
				Long nextRSMParent=rsmParentIdsIt.next();
				Long nextRSParent=rsIdByRsmId.get(nextRSMParent);
				
				//Add rs.merged_rsm_id 
				LOG.trace("Insert insertRSMergedRSMStmnt "+nextRSMParent+" for "+nextRSParent);
				insertRSMergedRSMStmnt.setLong(1,nextRSMParent);
				insertRSMergedRSMStmnt.setLong(2,nextRSParent);
				insertRSMergedRSMStmnt.execute();
					
				
				Iterator<Long> rsmChildsIt = rsmChildsByParents.get(nextRSMParent).iterator();		
				//Add rs hierarchy
				while(rsmChildsIt.hasNext()){
					Long nextRSMChild = rsmChildsIt.next();
					Long nextRSChild = rsIdByRsmId.get(nextRSMChild);
					LOG.trace("Insert insertRSRelationStmnt "+nextRSParent+" for "+nextRSChild+" ? ");
					
					//test if relation not already registered (problem with flyway migration !)
					testIfRSRelationExistStmnt.setLong(1,nextRSParent);
					testIfRSRelationExistStmnt.setLong(2,nextRSChild);
					ResultSet queryRS = testIfRSRelationExistStmnt.executeQuery();					
					if(!queryRS.next()) {
						
						LOG.trace("Insert insertRSRelationStmnt "+nextRSParent+" for "+nextRSChild +" YES !! ");
						insertRSRelationStmnt.setLong(1, nextRSParent);
						insertRSRelationStmnt.setLong(2, nextRSChild );
						insertRSRelationStmnt.execute();
					}
				}				
			}
		
			
	        
        } finally {
            statement.close();
            if(insertRSRelationStmnt!=null && !insertRSRelationStmnt.isClosed())
            	insertRSRelationStmnt.close();
            if(insertRSMergedRSMStmnt!=null && !insertRSMergedRSMStmnt.isClosed())
            	insertRSMergedRSMStmnt.close();
        }
		
	}

}