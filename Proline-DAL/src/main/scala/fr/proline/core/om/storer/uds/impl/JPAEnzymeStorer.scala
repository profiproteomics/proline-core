package fr.proline.core.om.storer.uds.impl

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.IExecutionContext
import fr.profi.chemistry.model.Enzyme
import fr.proline.core.om.storer.uds.IEnzymeStorer
import fr.proline.core.orm.uds.repository.UdsEnzymeRepository
import fr.proline.core.orm.uds.{
  Enzyme => UdsEnzyme,
  EnzymeCleavage => UdsEnzymeCleavage
}
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions

object JPAEnzymeStorer extends IEnzymeStorer with LazyLogging {
  
  /**
   * Instantiates the PtmDefinition writer and call the insertPtmDefinitions method.
   */
  def storeEnzymes( enzymes: Seq[Enzyme], execCtx: IExecutionContext ): Int = {
    val writer = new JPAEnzymeWriter(execCtx)
    writer.insertEnzymes(enzymes)
  }

  private class JPAEnzymeWriter(execCtx: IExecutionContext) extends LazyLogging {

    // Make some requirements
    require(execCtx != null, "execCtx must not be null")
    require(execCtx.isJPA, "execCtx must be in JPA mode")

    // Define some vars
    val udsDbCtx = execCtx.getUDSDbConnectionContext()
    val udsEM = udsDbCtx.getEntityManager()

    /**
     * Persists or merges a sequence of Enzyme objects into UDS Db.
     *
     * Transaction on UDS {{{EntityManager}}} should be opened by client code.
     *
     */
    def insertEnzymes(enzymes: Seq[Enzyme]): Int = {
      require(enzymes != null, "enzymes must not be null")
      
      var insertedEnzymes = 0

      // Iterate over each provided enzyme
      for (enzyme <- enzymes if enzyme.id < 0 ) {
        
        val enzymeName = enzyme.name

        // Try to retrieve an enzyme with its name and cache it if it exists
        val udsEnzyme = UdsEnzymeRepository.findEnzymeForName(udsEM, enzymeName)

        // IF the enzyme name doesn't exist in the database
        if (udsEnzyme == null) {
 
          // If no cleavage sites are given
          if( enzyme.enzymeCleavages.size == 0 ) {
//            throw new IllegalArgumentException("the enzyme cleavages must be defined for insertion in the database")
            throw new IllegalArgumentException("Unable to store enzyme '"+enzymeName+"' (not enough information)")
          } else {
            
            logger.info("Insert new Enzyme "+enzymeName)
            
            // Build a new enzyme
            val udsEnzyme = new UdsEnzyme()
            udsEnzyme.setName(enzyme.name)
            udsEnzyme.setCleavageRegexp(enzyme.cleavageRegexp.getOrElse(null))
            udsEnzyme.setIsIndependant(enzyme.isIndependant)
            udsEnzyme.setIsSemiSpecific(enzyme.isSemiSpecific)
            // Persist the new enzyme
            udsEM.persist(udsEnzyme)
            
            // Update the provided Enzyme id
            enzyme.id = udsEnzyme.getId()
            
            // Build enzyme cleavages
            val udsEnzymeCleavages = new ArrayBuffer[UdsEnzymeCleavage]
            enzyme.enzymeCleavages.foreach(ec => {
              val udsEnzymeCleavage = new UdsEnzymeCleavage()
              udsEnzymeCleavage.setSite(ec.site)
              udsEnzymeCleavage.setResidues(ec.residues)
              udsEnzymeCleavage.setRestrictiveResidues(ec.restrictiveResidues.getOrElse(null))
              udsEnzymeCleavage.setEnzyme(udsEnzyme)
              udsEM.persist(udsEnzymeCleavage)
              udsEnzymeCleavages += udsEnzymeCleavage
              ec.id = udsEnzymeCleavage.getId()
            })
            udsEM.merge(udsEnzyme)

            // Increase the number of inserted enzymes
            insertedEnzymes += 1
          }
        } else logger.info("Enzyme "+enzymeName+" already exist")
        
        // Synchronize the entity manager with the UDSdb if we are inside a transaction
        if( udsDbCtx.isInTransaction() ) udsEM.flush()
      }

      insertedEnzymes
    }

  }

}