package fr.proline.core.om.storer.ps.impl

import com.weiglewilczek.slf4s.Logging
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.asJavaSet
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.storer.ps.IPtmDefinitionStorer
import fr.proline.core.orm.ps.repository.PsPtmRepository
import fr.proline.core.orm.ps.{
  Ptm => PsPtm,
  PtmClassification => PsPtmClassification,
  PtmEvidence => PsPtmEvidence,
  PtmSpecificity => PsPtmSpecificity
}
import fr.proline.util.{ MathUtils, StringUtils }

import fr.proline.core.utils.ResidueUtils._

/**
 * @author David Bouyssie
 *
 * JPA implementation of PtmDefinition storer.
 *
 */
object JPAPtmDefinitionStorer extends IPtmDefinitionStorer with Logging {
  
  /**
   * Instantiates the PtmDefinition writer and call the insertPtmDefinitions method.
   */
  def storePtmDefinitions(ptmDefs: Seq[PtmDefinition], execCtx: IExecutionContext): Int = {
    val writer = new JPAPtmDefinitionWriter(execCtx)
    writer.insertPtmDefinitions(ptmDefs)
  }

  private class JPAPtmDefinitionWriter(execCtx: IExecutionContext) extends Logging {

    val PsPtmEvidencePrecursorType = fr.proline.core.orm.ps.PtmEvidence.Type.Precursor

    // Make some requirements
    require(execCtx != null, "execCtx must not be null")
    require(execCtx.isJPA, "execCtx must be in JPA mode")

    // Define some vars
    val psEM = execCtx.getPSDbConnectionContext().getEntityManager()
    
    // Retrieve the list of existing PTM classifications
    val allPtmClassifs = psEM.createQuery("SELECT e FROM fr.proline.core.orm.ps.PtmClassification e",classOf[PsPtmClassification]).getResultList().toList
    val psPtmClassifByUpperName = Map() ++ allPtmClassifs.map( classif => classif.getName.toUpperCase() -> classif )

    /**
     * Persists or merges a sequence of PtmDefinition objects into PS Db.
     *
     * Transaction on PS {{{EntityManager}}} should be opened by client code.
     *
     * New PtmDefinitions are stored using the following procedure:
     * 1. IF the PTM short name already exists in the database
     *    1.1 IF the PTM Precursor delta is different -> throw EXCEPTION (attempt to store different properties of an already existing PTM)
     *    1.2 ELSE IF the PTM Precursor delta is identical but the specificity is new -> SAVE new SPECIFICITY and UPDATE the provided PtmDefinition id
     *    1.3 ELSE (the PTM Precursor delta and the specificity are identical in the PSdb) -> UPDATE the provided PtmDefinition id
     *
     * 2. IF the PTM short name doesn't exist in the database
     *    // Check the PTM composition is available (it is required to search for PTM Precursor evidences)
     *    2.1 IF the PTM composition is not defined -> throw EXCEPTION
     *    2.2 ELSE
     *        2.2.1 IF a PTM exists with the same Precursor delta (composition matching) -> throw EXCEPTION
     *        2.2.2 ELSE (no PTM for this Precursor delta in the database) -> SAVE new PTM and UPDATE the provided PtmDefinition id
     *
     * Note: PTM classification ambiguities are ignored
     *
     */
    def insertPtmDefinitions(ptmDefs: Seq[PtmDefinition]): Int = {
      require(ptmDefs != null, "ptmDefs must not be null")
      
      var insertedPtmDefs = 0

      // Iterate over each provided PTM definition
      for (ptmDef <- ptmDefs if ptmDef.id < 0 ) {
        
        val ptmShortName = ptmDef.names.shortName

        // Try to retrieve a PTM with the same short name
        val psPtm = PsPtmRepository.findPtmForShortName(psEM, ptmShortName)

        // IF the PTM short name already exists in the database
        if (psPtm != null) {

          // Retrieve the Precursor delta of the found PTM
          val psPtmPrecDelta = psPtm.getEvidences().toList.find(_.getType() == PsPtmEvidencePrecursorType).get

          // Compare PS PTM evidence with found PTM evidence
          val precursorDelta = ptmDef.precursorDelta
          val ptmComposition = precursorDelta.composition
          if (nearlyEqual(psPtmPrecDelta.getMonoMass, precursorDelta.monoMass) == false ||
              nearlyEqual(psPtmPrecDelta.getAverageMass, precursorDelta.averageMass) == false ||
            (StringUtils.isEmpty(ptmComposition) == false && ptmComposition != ptmComposition)) {
            throw new IllegalArgumentException("the provided PTM %s exists in the PSdb with different evidence properties".format(ptmShortName))
          }

          // Try to retrieve the PTM specificity matching the provided PTM definition
          val psPtmSpecifs = psPtm.getSpecificities().toList
          val psMatchingPtmSpecifOpt = psPtmSpecifs.find { psPtmSpecif =>
            psPtmSpecif.getLocation == ptmDef.location && psPtmSpecif.getResidue == ptmDef.residue
          }

          // IF the PTM Precursor delta is identical but the specificity is new
          if (psMatchingPtmSpecifOpt.isEmpty) {

            // Save a new specificity
            val psPtmSpecificity = convertPtmDefinitionToPSPtmSpecificity(ptmDef,psPtm)
            psEM.persist( psPtmSpecificity )
            
            // Update the provided PtmDefinition id
            ptmDef.id = psPtmSpecificity.getId()
            
            // Increase the number of inserted PTM definitions
            insertedPtmDefs += 1
          
          // ELSE IF the PTM Precursor delta and the specificity are identical in the PSdb
          } else {
            // Update the provided PtmDefinition id
            ptmDef.id = psMatchingPtmSpecifOpt.get.getId()
          }

        // IF the PTM short name doesn't exist in the database
        } else {

          // IF the PTM evidence is not fulfilled
          if( ptmDef.isCompositionDefined == false ) {
            throw new IllegalArgumentException("the PTM composition must be defined for insertion in the database")
          } else {
            
            // IF a PTM exists with the same Precursor evidence (composition matching)
            if( findPtmEvidencesByComposition(ptmDef.precursorDelta.composition).length > 0 ) {
              throw new IllegalArgumentException("a PTM evidence with the same composition already exists")
            }
            // ELSE (no PTM for this Precursor evidence in the database) -> SAVE new PTM
            else {
              
              // Build and persist the precursor delta
              val ptmPrecDelta = ptmDef.precursorDelta
              val psPtmPrecDelta = convertPtmEvidenceToPSPtmEvidence(ptmPrecDelta)              
              psEM.persist(psPtmPrecDelta)
              
              // Build and persist a new PTM
              val psPtm = new PsPtm()
              psPtm.setShortName(ptmDef.names.shortName)
              psPtm.setFullName(ptmDef.names.fullName)
              psPtm.setEvidences( asJavaSet(Set(psPtmPrecDelta)) )
              psPtm.setUnimodId(ptmDef.unimodId)
              psEM.persist(psPtm)
              
              // Build and persist the PTM specificity
              val psPtmSpecif = convertPtmDefinitionToPSPtmSpecificity(ptmDef,psPtm)
              psEM.persist(psPtmSpecif)
              
              // Increase the number of inserted PTM definitions
              insertedPtmDefs += 1
            }
          }          
        }
      }

      insertedPtmDefs
    }

    protected def convertPtmEvidenceToPSPtmEvidence(ptmEvidence: PtmEvidence): PsPtmEvidence = {
      
      val psPtmEvidence = new PsPtmEvidence()
      psPtmEvidence.setType( PsPtmEvidence.Type.valueOf(ptmEvidence.ionType.toString()) )
      psPtmEvidence.setIsRequired(ptmEvidence.isRequired)
      psPtmEvidence.setComposition(ptmEvidence.composition)
      psPtmEvidence.setMonoMass(ptmEvidence.monoMass)
      psPtmEvidence.setAverageMass(ptmEvidence.averageMass)
      
      psPtmEvidence
    }
    
    protected def convertPtmDefinitionToPSPtmSpecificity(ptmDef: PtmDefinition, psPtm: PsPtm): PsPtmSpecificity = {
      require( ptmDef.classification != null, "PTM classification must be defined" )
      
      // Retrieve the corresponding classification
      // TODO: create an enumeration of classifications
      val psPtmClassif = psPtmClassifByUpperName( ptmDef.classification.toUpperCase()  )
      
      val psPtmSpecificity = new PsPtmSpecificity()
      psPtmSpecificity.setLocation(ptmDef.location)
      psPtmSpecificity.setResidue(scalaCharToCharacter(ptmDef.residue))
      psPtmSpecificity.setClassification( psPtmClassif )
      psPtmSpecificity.setPtm(psPtm)
      
      // Retrieve evidences belongings to the PTM specificity
      val specificityEvidences = ptmDef.ptmEvidences.filter( ev => ev.ionType != IonTypes.Precursor )
      val psSpecificityEvidences = specificityEvidences.map( convertPtmEvidenceToPSPtmEvidence(_) )
      psPtmSpecificity.setEvidences( asJavaSet(psSpecificityEvidences.toSet) )
      
      psPtmSpecificity
    }
    
    // TODO: put in a repository
    protected def findPtmEvidencesByComposition( composition: String ): List[PsPtmEvidence] = {
      require( StringUtils.isNotEmpty(composition), "composition must not be an empty string" )
      
      val query = psEM.createQuery("FROM fr.proline.core.orm.ps.PtmEvidence WHERE composition = :composition",classOf[PsPtmEvidence])
      query.setParameter("composition", composition).getResultList().toList
    }
    
    // TODO: put in a repository
    /*protected def findPtmEvidencesByComposition() {
        /*  msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.msi.ResultSet])
      .setParameter("ids", identRsIds).getResultList().toList*/
    }*/

    protected def nearlyEqual(a: Double, b: Double): Boolean = {
      (a - b).abs < MathUtils.EPSILON_HIGH_PRECISION
    }

    /*
     * Here is a more robust solution but not usefull there
     * Source : http://floating-point-gui.de/errors/comparison/
     *
     * TODO: put in Math Utils ???
     * 
public static boolean nearlyEqual(float a, float b, float epsilon) {
    final float absA = Math.abs(a);
    final float absB = Math.abs(b);
    final float diff = Math.abs(a - b);

    if (a == b) { // shortcut, handles infinities
      return true;
    } else if (a == 0 || b == 0 || diff < Float.MIN_NORMAL) {
      // a or b is zero or both are extremely close to it
      // relative error is less meaningful here
      return diff < (epsilon * Float.MIN_NORMAL);
    } else { // use relative error
      return diff / (absA + absB) < epsilon;
    }
  }
     */

  }

}