package fr.proline.core.om.storer.ps.impl

import com.typesafe.scalalogging.LazyLogging
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.setAsJavaSet
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

    val PsPtmEvidencePrecursorType = fr.proline.core.orm.ps.PtmEvidence.Type.Precursor

    // Make some requirements
    require(execCtx != null, "execCtx must not be null")
    require(execCtx.isJPA, "execCtx must be in JPA mode")

    // Define some vars
    val psDbCtx = execCtx.getPSDbConnectionContext()
    val psEM = psDbCtx.getEntityManager()

    // Retrieve the list of existing PTM classifications
    val allPtmClassifs = psEM.createQuery("SELECT e FROM fr.proline.core.orm.ps.PtmClassification e", classOf[PsPtmClassification]).getResultList().toList
    val psPtmClassifByUpperName = Map() ++ allPtmClassifs.map(classif => classif.getName.toUpperCase() -> classif)

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
        val psPtm = PsPtmRepository.findPtmForShortName(psEM, ptmShortName)

        // IF the PTM short name already exists in the database
        if (psPtm != null) {

          // Retrieve the Precursor delta of the found PTM
          val psPtmPrecDelta = psPtm.getEvidences().toList.find(_.getType() == PsPtmEvidencePrecursorType).get

          // Compare PS PTM evidence with found PTM evidence
          val precursorDelta = ptmDef.precursorDelta
          val ptmComposition = precursorDelta.composition

          val precursorDeltaCompositionSorted = precursorDelta.composition.split(" ").sorted.mkString(" ")
          val psPtmPrecDeltaCompositionSorted = psPtmPrecDelta.getComposition.split(" ").sorted.mkString(" ")

          if (
            nearlyEquals(psPtmPrecDelta.getMonoMass, precursorDelta.monoMass, 1E-5) == false ||
            nearlyEquals(psPtmPrecDelta.getAverageMass, precursorDelta.averageMass, MathUtils.EPSILON_LOW_PRECISION) == false ||
            precursorDeltaCompositionSorted != psPtmPrecDeltaCompositionSorted
          ) {
            throw new IllegalArgumentException(s"the provided PTM $ptmShortName exists in the PSdb with different evidence properties")
          }

          // Try to retrieve the PTM specificity matching the provided PTM definition
          val psPtmSpecifs = Option(psPtm.getSpecificities()).map(_.toSeq).getOrElse(Seq())

          val psMatchingPtmSpecifOpt = psPtmSpecifs.find { psPtmSpecif =>
            psPtmSpecif.getLocation == ptmDef.location && characterToScalaChar(psPtmSpecif.getResidue) == ptmDef.residue
          }

          // IF the PTM Precursor delta is identical but the specificity is new
          if (psMatchingPtmSpecifOpt.isEmpty) {

            val ptmSpecifLocation = if (ptmDef.residue != '\0') ptmDef.residue else ptmDef.location
            logger.info(s"Insert new PTM specifity at location '$ptmSpecifLocation' for PTM $ptmShortName")

            // Save a new specificity
            val psPtmSpecificity = convertPtmDefinitionToPSPtmSpecificity(ptmDef, psPtm)
            psPtm.addSpecificity(psPtmSpecificity)
            psEM.persist(psPtmSpecificity)

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
          if (ptmDef.isCompositionDefined == false) {
            throw new IllegalArgumentException("the PTM composition must be defined for insertion in the database")
          } else {

            logger.info(s"Insert new PTM $ptmShortName")
            // Build and persist the precursor delta
            val ptmPrecDelta = ptmDef.precursorDelta
            val psPtmPrecDelta = convertPtmEvidenceToPSPtmEvidence(ptmPrecDelta)
            psEM.persist(psPtmPrecDelta)

            // Build and persist a new PTM
            val psPtm = new PsPtm()
            psPtm.setShortName(ptmDef.names.shortName)
            psPtm.setFullName(ptmDef.names.fullName)
            psPtm.setEvidences(setAsJavaSet(Set(psPtmPrecDelta)))

            if (ptmDef.unimodId > 0) psPtm.setUnimodId(ptmDef.unimodId)

            psEM.persist(psPtm)

            psPtmPrecDelta.setPtm(psPtm)
            psEM.merge(psPtmPrecDelta)

            // Build and persist the PTM specificity
            val psPtmSpecif = convertPtmDefinitionToPSPtmSpecificity(ptmDef, psPtm)
            psEM.persist(psPtmSpecif)

            // Update the provided PtmDefinition id
            ptmDef.id = psPtmSpecif.getId()

            // Increase the number of inserted PTM definitions
            insertedPtmDefs += 1
          }
        }

        // Synchronize the entity manager with the PSdb if we are inside a transaction
        if (psDbCtx.isInTransaction()) psEM.flush()
      }

      insertedPtmDefs
    }

    protected def convertPtmEvidenceToPSPtmEvidence(ptmEvidence: PtmEvidence): PsPtmEvidence = {

      this.logger.info(s"Creating PTM evidence of type=${ptmEvidence.ionType} and mass=${ptmEvidence.monoMass}")
      val psPtmEvidence = new PsPtmEvidence()
      psPtmEvidence.setType(PsPtmEvidence.Type.valueOf(ptmEvidence.ionType.toString()))
      psPtmEvidence.setIsRequired(ptmEvidence.isRequired)
      psPtmEvidence.setComposition(ptmEvidence.composition)
      psPtmEvidence.setMonoMass(ptmEvidence.monoMass)
      psPtmEvidence.setAverageMass(ptmEvidence.averageMass)
      psPtmEvidence
    }

    protected def convertPtmDefinitionToPSPtmSpecificity(ptmDef: PtmDefinition, psPtm: PsPtm): PsPtmSpecificity = {
      require(ptmDef.classification != null, "PTM classification must be defined")

      // Retrieve the corresponding classification
      // TODO: create an enumeration of classifications
      val psPtmClassif = psPtmClassifByUpperName(ptmDef.classification.toUpperCase())

      val psPtmSpecificity = new PsPtmSpecificity()
      psPtmSpecificity.setLocation(ptmDef.location)
      psPtmSpecificity.setResidue(scalaCharToCharacter(ptmDef.residue))
      psPtmSpecificity.setClassification(psPtmClassif)
      psPtmSpecificity.setPtm(psPtm)

      // Retrieve evidences belongings to the PTM specificity
      val specificityEvidences = ptmDef.ptmEvidences.filter(ev => ev.ionType != IonTypes.Precursor)
      val psSpecificityEvidences = specificityEvidences.map { ptmSpecif =>
        val convertedEvidence = convertPtmEvidenceToPSPtmEvidence(ptmSpecif)
        convertedEvidence.setPtm(psPtm)
        convertedEvidence.setSpecificity(psPtmSpecificity)
        convertedEvidence
      }
      psPtmSpecificity.setEvidences(setAsJavaSet(psSpecificityEvidences.toSet))

      psPtmSpecificity
    }

    // TODO: put in a repository
    protected def findPtmEvidencesByComposition(composition: String): List[PsPtmEvidence] = {
      require(StringUtils.isNotEmpty(composition), "composition must not be an empty string")

      val query = psEM.createQuery("FROM fr.proline.core.orm.ps.PtmEvidence WHERE composition = :composition", classOf[PsPtmEvidence])
      query.setParameter("composition", composition).getResultList().toList
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