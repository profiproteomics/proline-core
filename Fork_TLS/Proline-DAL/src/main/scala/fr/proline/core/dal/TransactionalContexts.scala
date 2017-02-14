package fr.proline.core.dal

import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.IExecutionContext
import fr.proline.context.DatabaseConnectionContext

package object context {
  
  abstract class AbstractTransactionalContext extends LazyLogging {
    
    // TODO: decide to keep or not the returned Boolean value (it should not be returned in case of Exception)
    
    /**
     * Try to execute some code inside JDBC transactions.
     * Exceptions are not caught but transactions are always rolled back if they were not committed.
     * 
     * @param enabledTxByDbCtx Enabled transaction mapped by the corresponding database context.
     * @param txWork The code to be executed inside the transactions.
     * @return a Boolean value indicating the success of the transactions.
     */
    protected def tryInTransactions(
      enabledTxByDbCtx: Map[DatabaseConnectionContext,Boolean],
      txWork: => Unit
    ): Boolean = {
      
      // Map local transactions by database contexts
      val isLocalTxByDbCtx = enabledTxByDbCtx.map { case (dbCtx,txEnabled) =>
        // Set transaction as local if enabled and not already initiated
        dbCtx -> ( txEnabled && !dbCtx.isInTransaction() )
      }
      
      val isTxCommitedByDbCtx = HashMap() ++ enabledTxByDbCtx.keys.map( _ -> false )
  
      try {
        
        // Begin local transactions
        isLocalTxByDbCtx.map { case (dbCtx,isLocalTx) =>
          if( isLocalTx ) dbCtx.beginTransaction()
        }
        
        // Execute transactional work
        txWork
        
        // Commit local transactions
        isLocalTxByDbCtx.map { case (dbCtx,isLocalTx) =>
          if( isLocalTx ) dbCtx.commitTransaction()
          isTxCommitedByDbCtx(dbCtx) = true
        }
      
      } finally {
        
        isLocalTxByDbCtx.map { case (dbCtx,isLocalTx) =>
          if ( isLocalTx && isTxCommitedByDbCtx(dbCtx) == false) {
            try {
              dbCtx.rollbackTransaction()
            } catch {
              case ex: Exception => logger.error("Error rollbacking "+ dbCtx.getProlineDatabaseType() +" DB transaction", ex)
            }
          }
        }
      }
      
      // Check the number of local transactions equals the number of committed transactions
      isLocalTxByDbCtx.values.count(_ == true) == isTxCommitedByDbCtx.values.count(_ == true)
    }
    
  }
  
  class TransactionalDbConnectionContext(dbCtx: DatabaseConnectionContext) extends AbstractTransactionalContext {
    
    /**
     * Try to execute some code inside a JDBC transaction.
     * Exceptions are not caught but transactions are always rolled back if they were not committed.
     * 
     * @param txWork The code to be executed inside the transactions.
     * @return a Boolean value indicating the success of the transaction.
     */
    def tryInTransaction( txWork: => Unit ): Boolean = {
      this.tryInTransactions( Map( dbCtx -> true ), txWork)
    }
  }
  implicit def dbCtxToTxDbCtx(dbCtx: DatabaseConnectionContext) = new TransactionalDbConnectionContext(dbCtx)
  
  class TransactionalExecutionContext(execCtx: IExecutionContext) extends AbstractTransactionalContext  {
    
    /**
     * Try to execute some code inside JDBC transactions.
     * Exceptions are not caught but transactions are always rolled back if they were not committed.
     * 
     * @param udsTx Enable UDSdb transaction.
     * @param psTx Enable PSdb transaction.
     * @param pdiTx Enable PDIdb transaction.
     * @param msiTx Enable MSIdb transaction.
     * @param lcmsTx Enable LCMSdb transaction.
     * @param txWork The code to be executed inside the transactions.
     * @return a Boolean value indicating the success of the transactions.
     */
    def tryInTransactions(
      udsTx: Boolean = false,
      psTx: Boolean = false,
      pdiTx: Boolean = false,
      msiTx: Boolean = false,
      lcmsTx: Boolean = false,
      txWork: => Unit
    ): Boolean = {
      
      // Map enabled transaction by database contexts
      val enabledTxByDbCtx = Map(
        execCtx.getUDSDbConnectionContext -> udsTx,
        execCtx.getPSDbConnectionContext -> psTx,
        execCtx.getPDIDbConnectionContext -> pdiTx,
        execCtx.getMSIDbConnectionContext -> msiTx,
        execCtx.getLCMSDbConnectionContext -> lcmsTx
      )
      
      this.tryInTransactions(enabledTxByDbCtx, txWork)
    }
  }  
  implicit def execCtxToTxExecCtx(execCtx: IExecutionContext) = new TransactionalExecutionContext(execCtx)
  
}