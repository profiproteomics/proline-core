package fr.proline.core.om.provider.msi.impl

import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.utils.PeptidesOMConverterUtil
import fr.proline.core.orm.ps.repository.{ PsPtmRepository => psPtmRepo }
import fr.proline.core.orm.ps.PtmSpecificity
import javax.persistence.EntityManager
import javax.persistence.PersistenceException
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters.asJavaCollectionConverter
import fr.proline.context.DatabaseConnectionContext

class ORMPTMProvider(val psDbCtx: DatabaseConnectionContext) extends IPTMProvider with Logging {

  val converter = new PeptidesOMConverterUtil(true)

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Int]): Array[Option[PtmDefinition]] = {

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

  def getPtmDefinitions(ptmDefIds: Seq[Int]): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter(_ != None).map(_.get)
  }

  def getPtmDefinition(ptmName: String, ptmResidu: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    try {

      var ptmSpecificity: PtmSpecificity = null
      if (ptmResidu.equals('\0')) {
        ptmSpecificity = psPtmRepo.findPtmSpecificityForNameLocResidu(psDbCtx.getEntityManager, ptmName, ptmLocation.toString, null)
      } else {
        ptmSpecificity = psPtmRepo.findPtmSpecificityForNameLocResidu(psDbCtx.getEntityManager, ptmName, ptmLocation.toString, "" + ptmResidu)
      }
      if (ptmSpecificity == null)
        return None
      else
        return Some(converter.convertPtmSpecificityORM2OM(ptmSpecificity))
    } catch {

      case pEx: PersistenceException => {
        logger.error("Error while retrieving PtmSpecificity (name: " + ptmName + " residu: " + ptmResidu + " location: " + ptmLocation.toString + ")", pEx)

        return None
      }

    }

  }

  def getPtmId(shortName: String): Option[Int] = {
    try {
      val ptm = psDbCtx.getEntityManager.createQuery("FROM Ptm ptm WHERE ptm.shortName = :shortName", classOf[fr.proline.core.orm.ps.Ptm]).setParameter("shortName", shortName).getSingleResult
      if (ptm == null)
        return None
      else
        return Some(ptm.getId)
    } catch {

      case pEx: PersistenceException => {
        logger.error("Error while retrieving Ptm " + shortName, pEx)

        return None
      }

    }
    return None
  }

}