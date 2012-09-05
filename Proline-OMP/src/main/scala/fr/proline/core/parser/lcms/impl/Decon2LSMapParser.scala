package fr.proline.core.parser.lcms.impl

import fr.proline.core.parser.lcms.ILcmsMapFileParser
import fr.proline.core.om.model.lcms.{LcmsRun,LcmsScan, IsotopicPattern}
import collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.lcms.Peak
import fr.proline.core.om.model.lcms.RunMap


/*
case class Decon2LSParams(
    ms1MozTol: Double = 0.01,
    ms1MosTolUnit: String = "ppm",
    gapTol: Int = 2,
    minFtScanCount: Int = 2,
    elutionTimeUnit: String = "seconds"
    )

object Decon2LSMapParser {
  
  val sepChar = ","
    
  def getMozInDalton(mozOfRef:Double, ms1_mozTol:Double, ms1_mozTolUnit:String) = {
    mozOfRef * ms1_mozTol / 1e6
  }
}
    
    
class Decon2LSMapParser extends ILcmsMapFileParser {
  
<<<<<<< .mine
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: Decon2LSParams ) = {
    
=======
  def getRunMap( filePath: String, lcmsRun: LcmsRun, extraParams: Map [String,Any] ): Option[RunMap] = {
    null
>>>>>>> .r948
	////// Retrieve some vars from extra hash params
    val ms1MozTol = extraParams.ms1MozTol//extraParams.getOrElse("ms1_moz_tol", 0.01 ).asInstanceOf[Double]
    val ms1MozTolUnit = extraParams.ms1MosTolUnit
    val gapTol = extraParams.gapTol
    val minFtScanCount = extraParams.minFtScanCount
    val elutionTimeUnit = extraParams.elutionTimeUnit ////// may be minutes
    
    var timeConversionFactor:Int = 0
    elutionTimeUnit.toLowerCase match {
      case "seconds" => timeConversionFactor = 60
      case "minutes" => timeConversionFactor = 1
      case _ => throw new Exception("Invalid elution time unit")
    }
    val ms1Scans = this.getMs1Scans( filePath, lcmsRun.id )
    val cycleNum = 0
    var cycleNumByScanId = ms1Scans.map( (s) => s.id -> s.cycle) toMap 
    var timeByScanId =  ms1Scans.map( (s) => s.id -> s.time) toMap 
    
    
    val lines = io.Source.fromFile(filePath).getLines()
    val ftTableFields = lines.next.split(Decon2LSMapParser.sepChar)
    val isotopicPatterns = new ArrayBuffer[IsotopicPattern]
    var ipCount = 1
    
    //treatment of one row
    def getFeatureFromRow(row:String) {
      val values = row.split(Decon2LSMapParser.sepChar)
      val valuesAsHash = ftTableFields.map { _ -> values.take(0) } toMap
      //val ipProps = ( fwhm = valuesAsHash("fwhm"), snr = valuesAsHash("signal_noise"))
      
      val ip = new IsotopicPattern(
                      id = ipCount,
                      moz = valuesAsHash("mz").asInstanceOf[Double],
                      intensity = valuesAsHash("abundance").asInstanceOf[Float], 
                      charge = valuesAsHash("charge").asInstanceOf[Int],
                      fitScore = valuesAsHash("fit").asInstanceOf[Float],
                      scanInitialId = valuesAsHash("scan_num").asInstanceOf[Int],
                      peaks = null,
                      overlappingIPs = null
                      //properties = ipProps,
                      )
      isotopicPatterns += ip
      ipCount += 1
    }//end
    
    lines.foreach(getFeatureFromRow)
    
    val apexByIpId = isotopicPatterns.map( isp => (isp.id -> isp) ).toMap
    
    var ipsByCharge = new HashMap[Int, ArrayBuffer[IsotopicPattern]]()
    isotopicPatterns.foreach(isp => ipsByCharge.update(isp.charge, ipsByCharge.getOrElseUpdate(isp.charge, new ArrayBuffer[IsotopicPattern]) += isp))
    
    for ((charge, sameChargeIps) <- ipsByCharge) {
      val ipsSortedByInt = sameChargeIps.sortBy(ips => ips.intensity)
      val ipsGroupedByMoz = new HashMap[Int, ArrayBuffer[IsotopicPattern]]()
      sameChargeIps.foreach(value => ipsGroupedByMoz.update(value.moz.asInstanceOf[Int], ipsGroupedByMoz.getOrElse(value.moz.asInstanceOf[Int], new ArrayBuffer[IsotopicPattern]) += value))
      
      var processedIp = new ArrayBuffer[Int]()
      for (ips <- ipsSortedByInt) {
        val ipId = ips.id
        ////// don't process a previously processed isotopic pattern
        if (processedIp.indexOf(ipId) != -1 ) {//None) {
          
        
	        ////// Take the current ip as reference
	        processedIp(ipId) = 1
	        val mozOfRef = ips.moz
	        val scanIdOfRef = ips.scanInitialId
	        val cycleNumOfRef = cycleNumByScanId(scanIdOfRef)
	        
	        ////// Retrieve putative isotopic patterns which belong to the same feature (same moz range)
	        val mozInt = mozOfRef.asInstanceOf[Int]
	        val sameMozRangeIps = new ArrayBuffer[IsotopicPattern]
	        
	        for ( mozIndex <- mozInt-1 to mozInt+1) {
	        	sameMozRangeIps ++ ipsGroupedByMoz.getOrElse(mozIndex, new ArrayBuffer[IsotopicPattern])
	        }
	        
	        val sameMozIpsByScanId = new HashMap[Int, ArrayBuffer[HashMap[String, Any]]]()
	        
	        for( tmpIp <- sameMozRangeIps) {

	          val deltaMoz = Math.abs(tmpIp.moz - mozOfRef)
	          val mozTolInDalton = Decon2LSMapParser.getMozInDalton(mozOfRef, ms1MozTol, ms1MozTolUnit )
	          
	          if (deltaMoz < mozTolInDalton) {
	            sameMozIpsByScanId.update(tmpIp.scanInitialId, sameMozIpsByScanId.getOrElse(tmpIp.scanInitialId, new ArrayBuffer[HashMap[String, Any]]) += HashMap[String, Any]("ip"->tmpIp, "delta_moz"->deltaMoz))
	          }
	        }
	        ////// For each scan keep only the nearest moz from the reference moz 
           val sameFtIpByCycleNum = HashMap[Int, IsotopicPattern]()
           for( (scanId, tmpIpData) <- sameMozIpsByScanId) {
        	   val ipDataSortedByDeltaMoz = tmpIpData.sortBy(x => x("delta_moz") match {
        	     																		case k: Double => k
        	     																		case _ => throw new Exception("Unknown error")
        	   																			}
        			   										)                                   
        	   val cycleNum = cycleNumByScanId(scanId)
        	   val isp = ipDataSortedByDeltaMoz(0)("ip") match {
        	     case ip:IsotopicPattern => ip
        	     case _ => throw new Exception("Cannot convert to isotopic pattern")
        	   }
        	   sameFtIpByCycleNum += ( cycleNum -> isp)
          }
            ////// Sort cycle nums to retrieve cycle num range
          val sortedCycleNums = sameFtIpByCycleNum.keysIterator.toSeq.sortBy(x=>x)
          val firstCycleNum = sortedCycleNums(0)
          val lastCycleNum = sortedCycleNums(-1)
          
          val sameFtIps = ArrayBuffer[IsotopicPattern](ips)
          var apexId =0
          var apex = 0
          var apexIntensity = 0      
          var gapCount=0
          
          
          def extractIsotopicPattern(cycleNum:Int, ipPickerCb: IsotopicPattern => Unit ):Option[Int] ={//}: Option[Int] {
             val curIp = sameFtIpByCycleNum(cycleNum)
             var newIpId = Option.empty[Int]
          
             if( curIp == None) {
            	 gapCount += 1
                 if (gapCount > gapTol)
                   return None
             }
             else {
            	 val curIpId = curIp.id
            	if( defined processedIp(curIpId) ) return Some(curIpId) 
            
            ////// reset the gap counter
            gapCount = 0
            ipPickerCb( curIp )
            
            ////// check if this IP is higher than the current apex
            val curIpIntensity = curIp.intensity
            if( curIpIntensity > apexIntensity ) {
              apexIntensity = curIpIntensity
              apexId = curIpId
              apex = apexByIpId(apexId)
            }
            
            ////// Mark the current ip as processed
            processedIp(curIpId) = 1
            
            return Some(curIpId)
          }
             newIpId
             
          }
          
        }
      }
    }
  }
  
  
      
   
  def getMs1Scans( ms1_scanFile: String, runId: Int ): ArrayBuffer[LcmsScan] = {
    //val sepChar = "," 
    val lines = io.Source.fromFile(ms1_scanFile).getLines
    val scanTableFields = lines.next.split(Decon2LSMapParser.sepChar) 
    
    val scans = new ArrayBuffer[LcmsScan]()
    var cycleNum = 0
    
    //closure to treat one line of the file
    def getScanInfosFromRow(row: String) {
      val values = row.split(Decon2LSMapParser.sepChar) toList
      val valuesAsHash = scanTableFields.map { _ -> values.take(0) } toMap
      
      val msLevel = valuesAsHash("type").asInstanceOf[Int]
      if( msLevel == 1 ) 
        cycleNum += 1
      
      val scanInfo = new LcmsScan(
                          id = valuesAsHash("scan_num").asInstanceOf[Int],
                          initialId = valuesAsHash("scan_num").asInstanceOf[Int],
                          cycle = cycleNum,
                          time = valuesAsHash("scan_time").asInstanceOf[Float],
                          msLevel = msLevel,
                          tic = valuesAsHash("tic").asInstanceOf[Double],
                          basePeakMoz = valuesAsHash("bpi_mz").asInstanceOf[Double],
                          basePeakIntensity = valuesAsHash("bpi").asInstanceOf[Double],
                          runId = runId
                          )
      scans += scanInfo
    }//end closure
    
    lines.foreach(getScanInfosFromRow)
    scans
 }
    
    
    


}
*/


/*
package Pairs::Lcms::Parser::Map::Decon2LS

use MooseX::Declare

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Class definition
//


    
    val features
    while( val(charge, sameChargeIps) = each(ipsByCharge) ) {
      print "processing charge+ charged isotopic patterns...\n"
      
      ////// Sort iostopic patterns by intensity
      val ipsSortedByInt = sort { b.intensity <= a.intensity } sameChargeIps
      
      ////// Group isotopic patterns by mass integer
      val ipsGroupedByMoz
      push(@{ ipsGroupedByMoz( int(_.moz) ) }, _ ) for ipsSortedByInt
      
      val processedIp
      for( ip <- ipsSortedByInt ) {
        val ipId = ip.id
        ////// don't process a previously processed isotopic pattern
        next if processedIp(ipId)
        
        ////// Take the current ip as reference
        processedIp(ipId) = 1
        val mozOfRef = ip.moz
        val scanIdOfRef = ip.scanInitialId
        val cycleNumOfRef = cycleNumByScanId(scanIdOfRef)
        
        ////// Retrieve putative isotopic patterns which belong to the same feature (same moz range)
        val mozInt = int(mozOfRef)
        val sameMozRangeIps
        foreach val mozIndex (mozInt-1, mozInt, mozInt+1) {
          push( sameMozRangeIps, @{ipsGroupedByMoz(mozIndex)} ) if defined ipsGroupedByMoz(mozIndex)
        }
        
        ////// Group ips by scan id
        val sameMozIpsByScanId
        for( tmpIp <- sameMozRangeIps ) {
        
          die "undefined m/z value" . Dumper (tmpIp.moz, mozOfRef) if is_empty_string(tmpIp.moz) or is_empty_string(mozOfRef)
         
          val deltaMoz = abs(tmpIp.moz - mozOfRef)
          val mozTolInDalton = get_moz_tol_in_dalton( mozOfRef, ms1_mozTol, ms1_mozTolUnit )
          
          next if deltaMoz > mozTolInDalton
          push( @{sameMozIpsByScanId(tmpIp.scanInitialId)}, { ip = tmpIp, delta_moz = deltaMoz } )
          }
        
        ////// For each scan keep only the nearest moz from the reference moz 
        val sameFtIpByCycleNum
        while( val( scanId, tmpIpData ) = each(sameMozIpsByScanId) ) {
          val ipDataSortedByDeltaMoz = sort { a(delta_moz) <= b(delta_moz) }
                                                   tmpIpData
                                                   
          val cycleNum = cycleNumByScanId(scanId)
          die "undefined cycle number for scan id = 'scanId'" if !defined cycleNum
          
          sameFtIpByCycleNum( cycleNum ) = ipDataSortedByDeltaMoz(0)(ip)
        }
        
        ////// Sort cycle nums to retrieve cycle num range
        val sortedCycleNums = sort { a <= b } keys(sameFtIpByCycleNum)
        val firstCycleNum = sortedCycleNums(0)
        val lastCycleNum = sortedCycleNums(-1)
        
        val sameFtIps = ( ip )
        val apexId
        val apex
        val apexIntensity = 0      
        val gapCount=0
        
        val extractIsotopicPattern = sub {
          
          val( cycleNum, ipPickerCallback ) = _
          
          ////// check if an ip is defined for this cycle number
          val curIp = sameFtIpByCycleNum(cycleNum)
          
          if( ! curIp) {
            gapCount += 1
            return undef if gapCount > gapTol
          }
          else {
            val curIpId = curIp.id
            return curIpId if defined processedIp(curIpId)
            
            ////// reset the gap counter
            gapCount = 0
            ipPickerCallback.( curIp )
            
            ////// check if this IP is higher than the current apex
            val curIpIntensity = curIp.intensity
            if( curIpIntensity > apexIntensity ) {
              apexIntensity = curIpIntensity
              apexId = curIpId
              apex = apexByIpId(apexId)
            }
            
            ////// Mark the current ip as processed
            processedIp(curIpId) = 1
            
            return curIpId
          }
          
        }
        
        ////// forward extraction
        val ipPicker = sub { push(sameFtIps, _(0) ) }
        foreach val curCycleNum (cycleNumOfRef + 1 ..lastCycleNum) {
          val ipId = extractIsotopicPattern.( curCycleNum, ipPicker )
          last if !defined ipId
        }
        
        ////// backward extraction
        ipPicker = sub { unshift(sameFtIps, _(0) ) }
        gapCount=0
        for (val curCycleNum=cycleNumOfRef - 1curCycleNum>=firstCycleNumcurCycleNum--) {
          val ipId = extractIsotopicPattern.( curCycleNum, ipPicker )
          last if !defined ipId
        }
        
        //for (val curCycleNum=cycleNumOfRef - 1curCycleNum>=firstCycleNumcurCycleNum--) {
        //  ////// check if an ip is defined for this cycle number
        //  val curIp = sameFtIpByCycleNum(curCycleNum)
        //  
        //  if( ! curIp) {
        //    gapCount += 1
        //    last if gapCount > gapTol
        //  }
        //  else {
        //    next if defined processedIp(curIp.id)
        //    ////// reset the gap counter
        //    gapCount = 0
        //    unshift(sameFtIps, curIp)
        //    ////// Mark the current ip as processed
        //    processedIp(curIp.id) = 1
        //  }
        //}
        
        ////// Check if feature has enough scans
        val ftScanCount = scalar(sameFtIps)
        next if ftScanCount < minFtScanCount
        
        //val intensitySum = reduce { a.intensity + b.intensity } sameFtIps
        val dataPoints = map { (timeByScanId(_.scanInitialId),_.intensity) } sameFtIps
        val area = calc_area( dataPoints )
        
        ////// new FT with sameFtIps
        val ft = new Pairs::Lcms::Model::Feature (          
          moz = mozOfRef,
          intensity = area,
          charge = ip.charge,
          elution_time = timeByScanId(scanIdOfRef),
          ms1_count = scalar(sameFtIps),
          is_cluster = 0,
          first_scan_initial_id = sameFtIps(0).scanInitialId,
          last_scan_initial_id = sameFtIps(-1).scanInitialId,
          apex_scan_initial_id = apex.scanInitialId,
          isotopic_patterns = sameFtIps,
          //apex_id = apexId,
          // apex = apex,
          // isotopic_patterns = ( apex )
        )
        //die Dumper ft if !defined ft.firstScanInitialId
        push(features,ft)
      }
    }
    
      val pps = new Pairs::Lcms::Model::PeakPickingSoftware(
                    name = 'decon2ls',
                    version = '1.0.3092',
                    algorithm = 'unknown',
                    )
    
    ////// attach features to the map
    val mapCreationTimestamp = time
    val map = new Pairs::Lcms::Model::RunMap(
                    creation_timestamp = mapCreationTimestamp,
                    //modification_timestamp = mapCreationTimestamp,
                    //properties =
                    is_processed = 0,
                    run_id = run.id,
                    features = features,
                    peak_picking_software = pps,
                    )
    
    ////// return the map
    return map
  }
  
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Method: get_ms1_scans()
  //
  def getMs1_scans( ms1_scanFile: String ): Unit = {
    
    val sepChar = ',' ////// may be 
    
    ////// open SCAN file
    open(SCAN_FH,"<", ms1_scanFile) || die "can't open file ms1_scanFile: $!"
    
    ////// parse SCAN file header
    val row = <SCAN_FH>
    chomp row
    val scanTableFields = split(sepChar,row)
    
    ////// iterate over each row of the SCAN file to assign scans informations to LCMS features
    val scansInfo
    val cycleNum = 0
    
    while (row = <SCAN_FH> ) {
      chomp row
      
      val values = split(sepChar,row)
      val valuesAsHash = map {_ = shift(values)} scanTableFields
      // die Dumper valuesAsHash if !defined valuesAsHash(tic)
      
      if( valuesAsHash(type) == 1 ) { cycleNum += 1 }
      
      val scanInfo = new Pairs::Lcms::Model::Scan(
                          id = valuesAsHash(scan_num),
                          initial_id = valuesAsHash(scan_num),
                          cycle = cycleNum,
                          time = valuesAsHash(scan_time),
                          ms_level = valuesAsHash(type),
                          tic = valuesAsHash(tic),
                          base_peak_moz = valuesAsHash(bpi_mz),
                          base_peak_intensity = valuesAsHash(bpi),
                          )
                          
      push (scansInfo, scanInfo)
    } 
    close SCAN_FH
  
    return scansInfo
  }
  
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Function: calc_area()
  //
  def calcArea (): Unit = {
    val dataPoints = shift
    
    val numOfPoints = scalar(dataPoints)
    return 0 if numOfPoints == 0
    return dataPoints.(0).(1) if numOfPoints == 1
    
    val( area, prevXValue, prevYValue )
    for( dataPoint <- dataPoints ) {
      val x = dataPoint.(0)
      val y = dataPoint.(1)
      
      if( defined prevXValue )
        {
        val deltaX = x-prevXValue
        area += (y + prevYValue ) * deltaX / 2
        }
        
      prevXValue = x
      prevYValue = y
      }
    
    return area
  }


} ////// end of class


1

*/