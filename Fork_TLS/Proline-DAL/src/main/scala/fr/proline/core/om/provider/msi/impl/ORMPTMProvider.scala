package fr.proline.core.om.provider.msi.impl

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters.asJavaCollectionConverter

import com.typesafe.scalalogging.LazyLogging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.{ PtmDefinition, PtmLocation }
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.util.PeptidesOMConverterUtil
import fr.proline.core.orm.ps.PtmSpecificity
import fr.proline.core.orm.ps.repository.{ PsPtmRepository => psPtmRepo }
import fr.proline.core.util.ResidueUtils._

import fr.proline.core.util.ResidueUtils._

class ORMPTMProvider(val psDbCtx: DatabaseConnectionContext) extends IPTMProvider with LazyLogging {

  val converter = new PeptidesOMConverterUtil(true)

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {

    var foundPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]

    val ptmSpecificityORMs = psDbCtx.getEntityManager.createQuery("FROM fr.proline.core.orm.ps.PtmSpecificity ptm_specificity WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.ps.PtmSpecificity])
      .setParameter("ids", ptmDefIds.asJavaCollection).getResultList().toList

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
    val foundPtmSpecificity = psPtmRepo.findPtmSpecificityForNameLocResidu(psDbCtx.getEntityManager, ptmName, ptmLocation.toString, scalaCharToCharacter(ptmResidu))

    if (foundPtmSpecificity == null) {
      None
    } else {
      Some(converter.convertPtmSpecificityORM2OM(foundPtmSpecificity))
    }

  }

  
  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
//    var ptmToReturn: PtmDefinition = null
//    this.ptmDefinitionById.values.foreach(ptm => {
//      ptm.ptmEvidences.foreach(e => {
//        if (scala.math.abs(ptmMonoMass - e.monoMass) <= ptmMonoMassMargin
//          && ptm.residue == ptmResidue
//          && ptm.location == ptmLocation.toString) {
//          ptmToReturn = ptm
//        }
//      })
//    })
//    Some(ptmToReturn)
    None
  }
  def getPtmId(shortName: String): Option[Long] = {
    val foundPtm = psPtmRepo.findPtmForShortName(psDbCtx.getEntityManager, shortName)

    if (foundPtm == null) {
      None
    } else {
      Some(foundPtm.getId)
    }

  }

}