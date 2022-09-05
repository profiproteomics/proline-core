package fr.proline.core.om.provider.msi.impl

import com.typesafe.scalalogging.LazyLogging
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.util.PeptidesOMConverterUtil
import fr.proline.core.orm.msi.repository.{MsiPtmRepository => MsiPtmRepo}
import fr.proline.core.util.ResidueUtils._

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

class ORMPTMProvider(val msiDbCtx: MsiDbConnectionContext) extends IPTMProvider with LazyLogging {

  val converter = new PeptidesOMConverterUtil(true)

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {

    var foundPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]

    val ptmSpecificityORMs = msiDbCtx.getEntityManager.createQuery(
      "FROM fr.proline.core.orm.msi.PtmSpecificity ptm_specificity WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.msi.PtmSpecificity]
    ).setParameter("ids", ptmDefIds.asJavaCollection).getResultList.asScala.toList

    var resultIndex = 0
    ptmDefIds.foreach(ptmDefId => {
      // Current PtmDef not found. Store None and go to next ptmDef Id
      if (resultIndex >= ptmSpecificityORMs.length || ptmSpecificityORMs.apply(resultIndex).getId != ptmDefId) {
        foundPtmDefBuilder += None
      } else {
        //Current PtmDef found in Repository. Just save and go to next ptmDef Id and found ptmSpecificity
        foundPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORMs.apply(resultIndex)))
        resultIndex += 1
      }
    })

    if (resultIndex <= ptmSpecificityORMs.length - 1) {
      val msg = "Return PtmSpecificity from Repository was not stored in final result ! Some errors occured ! "
      logger.warn(msg)
      throw new Exception(msg)
    }

    foundPtmDefBuilder.result
  }

  def getPtmDefinitions(ptmDefIds: Seq[Long]): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter(_.isDefined).map(_.get)
  }

  def getPtmDefinition(ptmName: String, ptmResidu: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    val foundPtmSpecificity = MsiPtmRepo.findPtmSpecificityForNameLocResidue(msiDbCtx.getEntityManager, ptmName, ptmLocation.toString, scalaCharToCharacter(ptmResidu))

    if (foundPtmSpecificity == null) {
      None
    } else {
      Some(converter.convertPtmSpecificityORM2OM(foundPtmSpecificity))
    }

  }

  private lazy val ptmDefinitionById: Map[Long, PtmDefinition] = {

    var foundPtmDefBuilder = new HashMap[Long, PtmDefinition]
    msiDbCtx.getEntityManager.createQuery(
      "FROM fr.proline.core.orm.msi.PtmSpecificity ptm_specificity",
      classOf[fr.proline.core.orm.msi.PtmSpecificity]
    ).getResultList.asScala.foreach(ptmSpec => {
      val ptm = converter.convertPtmSpecificityORM2OM(ptmSpec)
      foundPtmDefBuilder.put(ptm.id, ptm)
    })
    foundPtmDefBuilder.toMap
    
  }
  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    var ptmToReturn: PtmDefinition = null
    this.ptmDefinitionById.values.foreach(ptm => {
      ptm.ptmEvidences.foreach(e => {
        if (scala.math.abs(ptmMonoMass - e.monoMass) <= ptmMonoMassMargin
          && ptm.residue == ptmResidue
          && ptm.location == ptmLocation.toString) {
          return Some(ptm)
        }
      })
    })
    Some(ptmToReturn)
  }
  def getPtmId(shortName: String): Option[Long] = {
    val foundPtm = MsiPtmRepo.findPtmForShortName(msiDbCtx.getEntityManager, shortName)

    if (foundPtm == null) {
      None
    } else {
      Some(foundPtm.getId)
    }

  }

}