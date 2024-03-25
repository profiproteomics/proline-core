package fr.proline.core.om.storer.msi.impl

import com.typesafe.scalalogging.LazyLogging
import scala.collection.JavaConverters._
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msi.IonTypes
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.storer.msi.IPtmDefinitionStorer
import fr.proline.core.orm.msi.repository.MsiPtmRepository
import fr.proline.core.orm.msi.{
  Ptm => MsiPtm,
  PtmClassification => MsiPtmClassification,
  PtmEvidence => MsiPtmEvidence,
  PtmSpecificity => MsiPtmSpecificity
}
import fr.profi.util.{ MathUtils, StringUtils }

import fr.proline.core.util.ResidueUtils._

/**
 * @author David Bouyssie
 *
 * JPA implementation of PtmDefinition storer.
 *
 */
object JPAPtmDefinitionStorer extends IPtmDefinitionStorer with LazyLogging {
  
  /**
   * Instantiates the PtmDefinition writer and call the insertPtmDefinitions method.
   */
  def storePtmDefinitions(ptmDefs: Seq[PtmDefinition], execCtx: IExecutionContext): Int = {
    val writer = new JPAPtmDefinitionWriter(execCtx)
    writer.insertPtmDefinitions(ptmDefs)
  }

  private class JPAPtmDefinitionWriter(execCtx: IExecutionContext) extends LazyLogging {

    val MsiPtmEvidencePrecursorType = fr.proline.core.orm.msi.PtmEvidence.Type.Precursor
    
    require(execCtx != null, "execCtx must not be null")
    
    val msiDbCtx = execCtx.getMSIDbConnectionContext()
    require(msiDbCtx.isJPA, "msiDbCtx must be in JPA mode")
    
    val msiEM = msiDbCtx.getEntityManager()

    // Retrieve the list of existing PTM classifications
    val allPtmClassifs = msiEM.createQuery("SELECT e FROM fr.proline.core.orm.msi.PtmClassification e",classOf[MsiPtmClassification]).getResultList().asScala.toList
    val msiPtmClassifByUpperName = Map() ++ allPtmClassifs.map( classif => classif.getName.toUpperCase() -> classif )

    val msiOtherPtmClassifName = MsiPtmClassification.PtmClassificationName.OTHER.toString().toUpperCase()
    val msiOtherPtmClassif = msiPtmClassifByUpperName(msiOtherPtmClassifName)

    /**
     * Persists or merges a sequence of PtmDefinition objects into MSI Db.
     *
     * Transaction on MSI {{{EntityManager}}} should be opened by client code.
     *
     * New PtmDefinitions are stored using the following procedure:
     * 1. IF the PTM short name already exists in the database
     *    1.1 IF the PTM Precursor delta is different -> throw EXCEPTION (attempt to store different properties of an already existing PTM)
     *    1.2 ELSE IF the PTM Precursor delta is identical but the specificity is new -> SAVE new SPECIFICITY and UPDATE the provided PtmDefinition id
     *    1.3 ELSE (the PTM Precursor delta and the specificity are identical in the MSIdb) -> UPDATE the provided PtmDefinition id
     *
     * 2. IF the PTM short name doesn't exist in the database
     *    // Check the PTM composition is available (it is required to search for PTM Precursor evidences)
     *    2.1 IF the PTM composition is not defined -> throw EXCEPTION
     *    2.2 ELSE SAVE new PTM and UPDATE the provided PtmDefinition id (PTM classification ambiguities are ignored here)
     *
     * TODO: create custom Exception classes ???
     */
    def insertPtmDefinitions(ptmDefs: Seq[PtmDefinition]): Int = {
      require(ptmDefs != null, "ptmDefs must not be null")

      var insertedPtmDefs = 0

      // Iterate over each provided PTM definition
      for (ptmDef <- ptmDefs if ptmDef.id < 0) {

        val ptmShortName = ptmDef.names.shortName

        // Try to retrieve a PTM with the same short name and cache it if it exists
        val msiPtm = MsiPtmRepository.findPtmForShortName(msiEM, ptmShortName)

        // IF the PTM short name already exists in the database
        if (msiPtm != null) {
           
          // Retrieve the Precursor delta of the found PTM
          val msiPtmPrecDelta: MsiPtmEvidence = msiPtm.getEvidences().asScala.toList.find(_.getType() == MsiPtmEvidencePrecursorType).get
          
          // Compare MSI PTM evidence with found PTM evidence
          val precursorDelta = ptmDef.precursorDelta

          val precursorDeltaCompositionSorted = precursorDelta.composition.split(" ").sorted.mkString(" ")
          val msiPtmPrecDeltaCompositionSorted = msiPtmPrecDelta.getComposition.split(" ").sorted.mkString(" ")
      
          if ( !nearlyEquals(msiPtmPrecDelta.getMonoMass, precursorDelta.monoMass, 1E-5)  ||
              !nearlyEquals(msiPtmPrecDelta.getAverageMass, precursorDelta.averageMass, MathUtils.EPSILON_LOW_PRECISION) ||
              precursorDeltaCompositionSorted != msiPtmPrecDeltaCompositionSorted ) { 
            throw new IllegalArgumentException("the provided PTM %s exists in the MSIdb with different evidence properties".format(ptmShortName))
          }

          // Try to retrieve the PTM specificity matching the provided PTM definition
          val msiPtmSpecifs = Option(msiPtm.getSpecificities()).map(_.asScala.toSeq).getOrElse(Seq())
          
          val msiMatchingPtmSpecifOpt = msiPtmSpecifs.find { msiPtmSpecif =>
            msiPtmSpecif.getLocation == ptmDef.location && characterToScalaChar(msiPtmSpecif.getResidue) == ptmDef.residue
          }
          
          // IF the PTM Precursor delta is identical but the specificity is new
          if (msiMatchingPtmSpecifOpt.isEmpty) {

            val ptmSpecifLocation = if (ptmDef.residue != '\u0000') ptmDef.residue else ptmDef.location
            logger.info(s"Insert new PTM specifity at location '$ptmSpecifLocation' for PTM $ptmShortName")

            // Save a new specificity
            val msiPtmSpecificity = convertPtmDefinitionToMsiPtmSpecificity(ptmDef,msiPtm)
            msiPtm.addSpecificity(msiPtmSpecificity)
            msiEM.persist( msiPtmSpecificity )

            // Update the provided PtmDefinition id
            ptmDef.id = msiPtmSpecificity.getId()

            // Increase the number of inserted PTM definitions
            insertedPtmDefs += 1

          // ELSE IF the PTM Precursor delta and the specificity are identical in the MSIdb
          } else {
            // Update the provided PtmDefinition id
            ptmDef.id = msiMatchingPtmSpecifOpt.get.getId()
          }

          // IF the PTM short name doesn't exist in the database
        } else {

          // IF the PTM evidence is not fulfilled
          if (!ptmDef.isCompositionDefined) {
            throw new IllegalArgumentException("the PTM composition must be defined for insertion in the database")
          } else {

            logger.info(s"Insert new PTM $ptmShortName")
            // Build and persist the precursor delta
            val ptmPrecDelta = ptmDef.precursorDelta
            val msiPtmPrecDelta = convertPtmEvidenceToMsiPtmEvidence(ptmPrecDelta)
            msiEM.persist(msiPtmPrecDelta)

            // Build and persist a new PTM
            val msiPtm = new MsiPtm()
            msiPtm.setShortName(ptmDef.names.shortName)
            msiPtm.setFullName(ptmDef.names.fullName)
            msiPtm.setEvidences( setAsJavaSet(Set(msiPtmPrecDelta)) )

            if( ptmDef.unimodId > 0 ) msiPtm.setUnimodId(ptmDef.unimodId)

            msiEM.persist(msiPtm)

            msiPtmPrecDelta.setPtm(msiPtm)
            msiEM.merge(msiPtmPrecDelta)

            // Build and persist the PTM specificity
            val msiPtmSpecif = convertPtmDefinitionToMsiPtmSpecificity(ptmDef,msiPtm)
            msiEM.persist(msiPtmSpecif)

            // Update the provided PtmDefinition id
            ptmDef.id = msiPtmSpecif.getId()

            // Increase the number of inserted PTM definitions
            insertedPtmDefs += 1
          }
        }

        // Synchronize the entity manager with the MSIdb if we are inside a transaction
        if( msiDbCtx.isInTransaction() ) msiEM.flush()
      }

      insertedPtmDefs
    }

    protected def convertPtmEvidenceToMsiPtmEvidence(ptmEvidence: PtmEvidence): MsiPtmEvidence = {

      this.logger.info(s"Creating PTM evidence of type=${ptmEvidence.ionType} and mass=${ptmEvidence.monoMass}")
      val msiPtmEvidence = new MsiPtmEvidence()
      msiPtmEvidence.setType( MsiPtmEvidence.Type.valueOf(ptmEvidence.ionType.toString()) )
      msiPtmEvidence.setIsRequired(ptmEvidence.isRequired)
      msiPtmEvidence.setComposition(ptmEvidence.composition)
      msiPtmEvidence.setMonoMass(ptmEvidence.monoMass)
      msiPtmEvidence.setAverageMass(ptmEvidence.averageMass)
      msiPtmEvidence
    }

    protected def convertPtmDefinitionToMsiPtmSpecificity(ptmDef: PtmDefinition, msiPtm: MsiPtm): MsiPtmSpecificity = {
      require(ptmDef.classification != null, "PTM classification must be defined")

      // Retrieve the corresponding classification
      // TODO: create an enumeration of classifications
      val msiPtmClassifOpt = msiPtmClassifByUpperName.get(ptmDef.classification.toUpperCase())
      if (msiPtmClassifOpt.isEmpty) {
        logger.warn( s"Can't find a PTM classification corresponding to '${ptmDef.classification}', will fallback to 'Other'.")
      }

      val msiPtmClassif = msiPtmClassifOpt.getOrElse(msiOtherPtmClassif)

      val msiPtmSpecificity = new MsiPtmSpecificity()
      msiPtmSpecificity.setLocation(ptmDef.location)
      msiPtmSpecificity.setResidue(scalaCharToCharacter(ptmDef.residue))
      msiPtmSpecificity.setClassification( msiPtmClassif )
      msiPtmSpecificity.setPtm(msiPtm)

      // Retrieve evidences belongings to the PTM specificity
      val specificityEvidences = ptmDef.ptmEvidences.filter( ev => ev.ionType != IonTypes.Precursor )
      val msiSpecificityEvidences = specificityEvidences.map { ptmSpecif => 
        val convertedEvidence = convertPtmEvidenceToMsiPtmEvidence(ptmSpecif)
        convertedEvidence.setPtm(msiPtm)
        convertedEvidence.setSpecificity(msiPtmSpecificity)
        convertedEvidence
      }
      msiPtmSpecificity.setEvidences( setAsJavaSet(msiSpecificityEvidences.toSet) )
      
      msiPtmSpecificity
    }

    // TODO: put in a repository
    protected def findPtmEvidencesByComposition( composition: String ): List[MsiPtmEvidence] = {
      require(StringUtils.isNotEmpty(composition), "composition must not be an empty string")

      val query = msiEM.createQuery("FROM fr.proline.core.orm.msi.PtmEvidence WHERE composition = :composition",classOf[MsiPtmEvidence])
      query.setParameter("composition", composition).getResultList().asScala.toList
    }

    protected def nearlyEquals(a: Double, b: Double, epsilon: Double): Boolean = {
      (a - b).abs < epsilon
    }

    /*
     * Here is a more robust solution but not usefull there
     * Source : http://floating-point-gui.de/errors/comparison/
     *
     * TODO: put in Math Utils ???
     * 
public static boolean nearlyEquals(float a, float b, float epsilon) {
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