package fr.proline.core.parser.lcms

import fr.proline.core.parser.lcms.impl._
import fr.proline.core.om.model.lcms.RunMap
import scala.collection.mutable.ArrayBuffer

trait ExtraParameters {}


trait ILcmsMapFileParser {
  
  import fr.proline.core.om.model.lcms.LcMsRun
  
  
  def getRunMap( filePath: String, lcmsRun: LcMsRun, extraParams: ExtraParameters ) : Option[RunMap]
  
  def getMs2Events(lcmsRun: LcMsRun, idx:Int) : Array[Int] = {
    /**
     * from the id of the ms2 scan taken at the apex, find all consecutive ms2
     * assume scans ordering same when the were acquired 
     */
    var ms2IdEvents = new ArrayBuffer[Int]
    var idxTmp = idx - 1
      while (lcmsRun.scans(idxTmp).msLevel == 2) {
        ms2IdEvents += lcmsRun.scans(idxTmp).initialId
        idxTmp -= 1
      }
      //go the right
      idxTmp = idx + 1
      while (lcmsRun.scans(idxTmp).msLevel == 2) {
        ms2IdEvents += lcmsRun.scans(idxTmp).initialId
        idxTmp += 1
      }
      ms2IdEvents.sortBy(i => i)
      ms2IdEvents toArray
  } 
  
}

object LcmsMapFileParser {
  
  def apply( fileType: String  ): ILcmsMapFileParser = { fileType match {
    //case "Decon2LS" => new Decon2LSMapParser()
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