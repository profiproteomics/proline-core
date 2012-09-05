package fr.proline.core.parser.lcms.impl

import java.util.Date

import scala.collection.mutable.ArrayBuffer

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.IsotopicPattern
import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.FeatureRelations
import fr.proline.core.om.model.lcms.PeakPickingSoftware
import fr.proline.core.parser.lcms.ExtraParameters


case class MFPaQMapParams extends ExtraParameters {
    var mapNumber: Int = 1 
}


object MFPaQMapParser {
  var sepChar = "\t"
  var nbColumns = 6
}

class MFPaQMapParser extends ILcmsMapFileParser {
  
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters) : Option[RunMap]= {
    val lines = io.Source.fromFile(filePath).getLines
    val columnNames = lines.next.stripLineEnd.split(MFPaQMapParser.sepChar).slice(1, MFPaQMapParser.nbColumns + 1)
    
    var mapNumber = extraParams.asInstanceOf[MFPaQMapParams].mapNumber
    var startingPoint = (mapNumber - 1) * MFPaQMapParser.nbColumns + 1  //interesting columns
    
    var features = ArrayBuffer[Feature]()
    
    while (lines.hasNext) {
      
      var l = lines.next.stripLineEnd.split(MFPaQMapParser.sepChar)
      
      var data = columnNames.zip(l.slice(startingPoint, startingPoint + MFPaQMapParser.nbColumns + 1)) toMap
      
      if (data("Score") != "") {
        
        var charge = l(0).replace("+","") toInt
        var moz = data("m/z") toDouble
        var intensity =  data("Area") toFloat
        var firstRt = data("First RT") toFloat
        var firstScan = lcmsRun.getScanAtTime(firstRt, 1)
        var lastRt = data("Last RT") toFloat
        var lastScan  = lcmsRun.getScanAtTime(lastRt, 1)
        var apexRt = data("Apex RT") toFloat
        var apexScan = lcmsRun.getScanAtTime(apexRt, 1)
        
        var ip = new IsotopicPattern(moz = moz,
        							 intensity = intensity,
        							 charge = charge,
        							 fitScore = Float.NaN,
        							 peaks = Array[Peak](),
        							 scanInitialId = apexScan.initialId,
        							 overlappingIPs = Array[IsotopicPattern]())
        
        var ms2EventIds = getMs2Events(lcmsRun, apexScan.initialId)
        
        var feature = Feature(id = Feature.generateNewId(),
        					  moz = moz,
        					  intensity = intensity,
        					  elutionTime = apexRt,
        					  charge = charge,
        					  qualityScore = Double.NaN,
        					  ms1Count = lastScan.cycle - firstScan.cycle + 1,
        					  ms2Count = ms2EventIds.length,
        					  isOverlapping = false,
        					  isotopicPatterns = Some(Array[IsotopicPattern](ip)),
        					  overlappingFeatures = Array[Feature](),
        					  relations = FeatureRelations(ms2EventIds, 
        							  					   firstScanInitialId = firstScan.initialId,
        							  					   lastScanInitialId = lastScan.initialId,
        							  					   apexScanInitialId = apexScan.initialId))
        features += feature
      }
      //nothing to do
      
    }//end while
    
    
    var runMap = new RunMap(id = lcmsRun.id,
      name = lcmsRun.rawFileName,
      isProcessed = false,
      creationTimestamp = new Date(),
      features = features toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "MFPaQ",
        "4.5",
        "unknown"))
    
    Some(runMap)
  }

}