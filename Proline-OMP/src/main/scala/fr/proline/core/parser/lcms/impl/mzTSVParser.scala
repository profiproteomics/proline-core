package fr.proline.core.parser.lcms.impl

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.RunMap

class mzTSVParser extends ILcmsMapFileParser {
  
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: Map [String,Any] ): Option[RunMap] = {
    null
  }

}