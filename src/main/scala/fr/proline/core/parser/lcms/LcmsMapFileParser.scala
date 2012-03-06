package fr.proline.core.om.parser.lcms

import fr.proline.core.om.parser.lcms.impl._

trait ILcmsMapFileParser {
  
  import fr.proline.core.om.lcms.LcmsRun
  
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