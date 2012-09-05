package fr.proline.core.parser.lcms.impl

import java.text.DateFormat
import java.util.Date


import scala.collection.mutable.ArrayBuffer

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.LcmsRun
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.lcms.{Feature, IsotopicPattern, FeatureRelations, Peak, PeakPickingSoftware}
import fr.proline.core.parser.lcms.ExtraParameters

object MsInspectMapParser {
  var sepChar = "\t"
}


class MsInspectMapParser extends ILcmsMapFileParser {

  def getRunMap(filePath: String, lcmsRun: LcmsRun, extraParams: ExtraParameters): Option[RunMap] = {
    val linesIterator = io.Source.fromFile(filePath).getLines()
    
    var line = if (linesIterator.hasNext) linesIterator.next else ""
    var timeStamp : Date = null
    
    while (line.startsWith("#")) {
      if (line.startsWith("# data=")) {
    	  var df : DateFormat = DateFormat.getDateInstance()
    	  timeStamp = df.parse(line.split("# data=")(1).stripLineEnd)
      }
      line = linesIterator.next
    }
    
    val columnNames = line.split(MsInspectMapParser.sepChar)
    
    var features = ArrayBuffer[Feature]()
    
    var id = 0
    
    while (linesIterator.hasNext) {
      var data = columnNames.zip(linesIterator.next.split(MsInspectMapParser.sepChar)) toMap
      var scanId = data("scan") toInt
      var elutionTime = data("time") toFloat
      var moz = data("mz") toDouble
      var intensity = data("totalIntensity") toFloat
      var charge = data("charge") toInt
      //var nbPeaks = data("peaks") toInt
      var firstScanId = data("scanFirst") toInt
      var lastScanId = data("scanLast") toInt
      
      var ms2EventIds = getMs2Events(lcmsRun, scanId)
      
      
      var ip = new IsotopicPattern(//id = id,
    		  					   moz = moz,
    		  					   intensity = intensity,
    		  					   charge = charge,
    		  					   fitScore = Float.NaN,
    		  					   peaks = Array[Peak](),
    		  					   scanInitialId = lcmsRun.scanById(scanId).initialId,
    		  					   overlappingIPs = Array[IsotopicPattern]())
      
      
      var feature = Feature(id = Feature.generateNewId(),
    		  				moz = moz,
    		  				intensity = intensity,
    		  				elutionTime = elutionTime,
    		  				charge = charge,
    		  				qualityScore = Double.NaN,
    		  				ms1Count = math.abs(lcmsRun.scanById(firstScanId).cycle - lcmsRun.scanById(lastScanId).cycle) + 1,
    		  				ms2Count = ms2EventIds.length,
    		  				isOverlapping = false,
    		  				isotopicPatterns = Some(Array[IsotopicPattern](ip)),
    		  				overlappingFeatures = Array[Feature](),
    		  				relations = FeatureRelations(ms2EventIds = ms2EventIds,
    		  											 firstScanInitialId = lcmsRun.scanById(firstScanId).initialId,
    		  											 lastScanInitialId = lcmsRun.scanById(lastScanId).initialId,
    		  											 apexScanInitialId = lcmsRun.scanById(scanId).initialId))
      
      id += 1
      features += feature
    }
     var runMap = new RunMap(id = lcmsRun.id,
      name = lcmsRun.rawFileName,
      isProcessed = false,
      creationTimestamp = timeStamp,
      features = features toArray,
      runId = lcmsRun.id,
      peakPickingSoftware = new PeakPickingSoftware(1,
        "MsInspect",
        "0.1",
        "unknown"))

    Some(runMap)
  }

}