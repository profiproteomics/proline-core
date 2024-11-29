package fr.proline.core.om.storer.msi

import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.orm.msi.{ResultSet => MsiResultSet, ResultSummary => MsiResultSummary}

import javax.persistence.EntityManager

trait IRsmDuplicator {

  /**
   * Clone source ResultSummary objects and attach them to empty RSM/RS
   * 
   */
    /**
     * Clone source ResultSummary objects and attach them to empty RSM/RS
     *  
     * @param sourceRSM : Merged ResultSummary to clone
     * @param emptyRSM : Empty ORM ResultSummary that will be filled with sourceRSM clone objects
     * @param emptyRS : Empty ORM ResultSet that will be filled with sourceRSM's ResultSet clone objec
     * @param eraseSourceIds : if true, sourceRSM data ids (RSM, ProtreinMatch ...) will be erased with new ORM ResultSummary ids 
     * @param msiEm EntityManager for MsiDB
     */
  def cloneAndStoreRSM(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet, eraseSourceIds : Boolean, msiEm: EntityManager): ResultSummary
  

}

