package fr.proline.core.parser.lcms

import fr.proline.core.parser.lcms.impl.OpenMSMapParser
import fr.proline.core.parser.lcms.impl.MaxQuantMapParser
import fr.proline.core.parser.lcms.impl.ProgenesisMapParser
import fr.proline.core.parser.lcms.impl.Decon2LSMapParser
import fr.proline.core.parser.lcms.impl.MFPaQMapParser
import fr.proline.core.parser.lcms.impl.MsInspectMapParser
import fr.proline.core.parser.lcms.impl.mzTSVParser


trait ILcmsMapFileParser {
  
  import fr.proline.core.om.model.lcms.LcmsRun
  
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: Map [String,Any] )
  
}

object LcmsMapFileParser {
  
  def apply( fileType: String  ): ILcmsMapFileParser = { fileType match {
    case "Decon2LS" => new Decon2LSMapParser()
    case "MaxQuant" => new MaxQuantMapParser()
    case "MFPaQ" => new MFPaQMapParser()
    case "MsInspect" => new MsInspectMapParser()
    case "mzTSV" => new mzTSVParser()
    case "OpenMS" => new OpenMSMapParser()
    case "Progenesis" => new ProgenesisMapParser()
    case _ => throw new Exception("unsupported file format: "+ fileType )
    }
  }

}