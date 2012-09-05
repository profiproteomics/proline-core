package fr.proline.core.parser.lcms.impl
import java.util.Date

import scala.xml.XML
import scala.xml.Elem
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.HashMap


import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.parser.lcms.ExtraParameters


class SuperHirnMapParser extends ILcmsMapFileParser {
  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters): Option[RunMap] = {
    val node = XML.load(io.Source.fromFile(filePath).getLines.toString)

    val nodeSequence = node \ OpenMSMapParser.targetLabel
    
    for (n <- nodeSequence) {
      var moz = (n \ "coordinate" \ "@mz")(0).text.toDouble
    }
    None
  }

}