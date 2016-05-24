package fr.proline.core.service.msi


import com.typesafe.scalalogging.LazyLogging

import java.sql.Connection

import scala.collection.mutable.ArrayBuffer

import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.repository.util.JDBCWork


/**
 * Service to remove orphan data in MSI database: RS and RSM
 * no check about the relation between child/parent relation (rs/rsm) => this check is done in the IHMs
 * @author MB243701
 */
class OrphanDataDeleter (
    execCtx: IExecutionContext, 
    projectId : Long, 
    resultSummaryIds: Array[Long], 
    resultSetIds: Array[Long]) extends IService with LazyLogging {
  
  
  def runService(): Boolean = {
    logger.info("Delete orphan data for projectId "+projectId)

    
    if (resultSummaryIds.length > 0) {
      logger.info("Delete orphan rsm  ("+resultSummaryIds.mkString(",")+")")
      var allRsmIds: ArrayBuffer[Long] = new ArrayBuffer()
      // load decoy RSM
      val jdbcWorkDecoy = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val decoyRSM = "SELECT decoy_result_summary_id FROM result_summary WHERE id IN (" + resultSummaryIds.mkString(",") + ") ORDER BY decoy_result_summary_id ASC";
          val sqlDecoyRSM = stmt.executeQuery(decoyRSM)
           while (sqlDecoyRSM.next) {
             val drsmId: Long = sqlDecoyRSM.getLong(1);
             allRsmIds += drsmId;
           }
          
           stmt.close()
           logger.debug("load decoy rsm in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWorkDecoy, false)
      
      allRsmIds = allRsmIds ++ resultSummaryIds //VDS TODO : Peut y avoir des doublons ! Manque la recuperation des target si selection des decoy 
      
      // todo: remove orphan object_tree from protein_set_object_tree_map
      // todo: remove orphan object_tree from 
       
      //remove rsm from result_summary_relation
      val jdbcWorkRsmRelation = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val delRSMRelationQuery = "DELETE FROM result_summary_relation "+
                    "WHERE parent_result_summary_id IN (" + allRsmIds.mkString(",") + ") OR child_result_summary_id in (" + allRsmIds.mkString(",") + ") ";
          val sqlDelRsmRel = stmt.executeUpdate(delRSMRelationQuery)
          stmt.close()
          logger.debug("delete rsm relation in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWorkRsmRelation, false)
      
      val jdbcWork = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val delRSMQuery = "DELETE FROM result_summary WHERE id IN (" + allRsmIds.mkString(",") + ")";
          val sqlDelRsm = stmt.executeUpdate(delRSMQuery)
          stmt.close()
          logger.debug("delete rsm in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWork, false)
    }
    
    if (resultSetIds.length > 0) {
      logger.info("Delete orphan rs  ("+resultSetIds.mkString(",")+")")
      
      // load decoy RS
      // TODO load msQuery to DEL, only if for identification dataset (not merge)
      var allRsIds: ArrayBuffer[Long] = new ArrayBuffer()
      var msiSearchIds: ArrayBuffer[Long] = new ArrayBuffer()
      val jdbcWorkDecoy = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val decoyRS = "SELECT decoy_result_set_id, msi_search_id FROM result_set WHERE id IN (" + resultSetIds.mkString(",") + ") ORDER BY decoy_result_set_id ASC";
          val sqlDecoyRS = stmt.executeQuery(decoyRS)
           while (sqlDecoyRS.next) {
             val drsId: Long = sqlDecoyRS.getLong(1);
             val msiSearchId: Long = sqlDecoyRS.getLong(2);
             allRsIds += drsId;
             
             if (!msiSearchIds.contains(msiSearchId)){
               msiSearchIds += msiSearchId
             }
            
           }
          
            stmt.close()
            logger.debug("search rs decoy and msiSearchId in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWorkDecoy, false)
      
      allRsIds = allRsIds ++ resultSetIds
      
      // todo remove orphan object_tree from result_set_object_tree_map
      // todo remove orphan object tree from peptide_match_object_tree_map
      
      
      // remove rs from result_set_relation
      val jdbcWorkRsRelation = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val delRSRelationQuery = "DELETE FROM result_set_relation "+
                    "WHERE parent_result_set_id IN (" + allRsIds.mkString(",") + ") OR child_result_set_id in (" + allRsIds.mkString(",") + ") ";
          val sqlDelRsRel = stmt.executeUpdate(delRSRelationQuery)
          stmt.close()
          logger.debug("delete rs relation in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWorkRsRelation, false)
      
      
      val jdbcWork = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val delRSQuery = "DELETE FROM result_set WHERE id IN (" + allRsIds.mkString(",") + ")";
          val sqlDelRs = stmt.executeUpdate(delRSQuery)
          stmt.close()
          logger.debug("delete rs in "+(System.currentTimeMillis-startTime)+" ms");

        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(jdbcWork, false)
      
      // TODO : del msQuery
      
      if (msiSearchIds.length > 0) {
        // get peaklist_id
        var peaklistIds: ArrayBuffer[Long] = new ArrayBuffer()
        val jdbcWorkDecoy = new JDBCWork() {
          override def execute(con: Connection) {
            val startTime = System.currentTimeMillis
            val stmt = con.createStatement()
            val peaklistQ = "SELECT distinct(peaklist_id) FROM msi_search WHERE id IN (" + msiSearchIds.mkString(",") + ") ORDER BY peaklist_id ASC";
            val sqlPeaklist = stmt.executeQuery(peaklistQ)
             while (sqlPeaklist.next) {
               val peaklistId: Long = sqlPeaklist.getLong(1);
               peaklistIds += peaklistId;
             }
          
              stmt.close()
            logger.debug("search peaklist in "+(System.currentTimeMillis-startTime));

          }
        } // End of jdbcWork anonymous inner class    
        execCtx.getMSIDbConnectionContext().doWork(jdbcWorkDecoy, false)
        
        
        logger.info("Delete orphan msiSearch  ("+msiSearchIds.mkString(",")+")")
        // delete from msi_seach
        val jdbcWorkMsiSearch = new JDBCWork() {
        override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
          val stmt = con.createStatement()
          val delMsiSearchQuery = "DELETE FROM msi_search WHERE id IN (" + msiSearchIds.mkString(",") + ")";
          val sqlDelMsiSearch = stmt.executeUpdate(delMsiSearchQuery)
          stmt.close();
          logger.debug("delete msiSearch in "+(System.currentTimeMillis-startTime)+" ms");

          }
        } // End of jdbcWork anonymous inner class    
        execCtx.getMSIDbConnectionContext().doWork(jdbcWorkMsiSearch, false)
        
        //delete from peaklist
        if (peaklistIds.length > 0) {
          logger.info("Delete orphan peaklist  ("+peaklistIds.mkString(",")+")")
          val jdbcWorkPeaklist = new JDBCWork() {
            override def execute(con: Connection) {
          val startTime = System.currentTimeMillis
              val stmt = con.createStatement()
              val delPeaklistQuery = "DELETE FROM peaklist WHERE id IN (" + peaklistIds.mkString(",") + ")";
              val sqlDelPeaklist = stmt.executeUpdate(delPeaklistQuery)
              stmt.close()
              logger.debug("delete peaklist in "+(System.currentTimeMillis-startTime)+" ms");

            }
          } // End of jdbcWork anonymous inner class    
          execCtx.getMSIDbConnectionContext().doWork(jdbcWorkPeaklist, false)
        }
      }
      
      
    }
    
    
    true
  }
  
}