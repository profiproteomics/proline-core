package fr.proline.core.parser.lcms.impl

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.parser.lcms.ExtraParameters


class MFPaQMapParser extends ILcmsMapFileParser {
  
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters) : Option[RunMap]= {
    None

  }

}