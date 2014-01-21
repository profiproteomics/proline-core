package fr.proline.core.om.storer.msi

import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.algo.msi.IResultSetSplitter
import fr.proline.core.om.model.msi.InstrumentConfig
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.PeaklistSoftware
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSetProperties
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.om.storer.msi.impl.SQLMsiSearchWriter
import fr.proline.util.StringUtils

/**
 * @author David Bouyssie
 *
 */
object ResultFileStorer extends Logging {

  /**
   * Store the IResultFile in current MSI db.
   * Transaction are not managed by this method, should be done by user.
   */
  def storeResultFile(
    storerContext: StorerContext,
    rsStorer: IRsStorer,
    resultFile: IResultFile,
    sqlCompatMode: Boolean, // TODO: remove me when SQL & JPA importers are removed
    targetDecoyMode: Option[String],
    acDecoyRegex: Option[util.matching.Regex] = None,
    saveSpectrumMatch: Boolean = false,
    rsSplitter: Option[IResultSetSplitter] = None
  ): Long = {

    val start = System.currentTimeMillis()
    logger.info("Storing ResultFile " + resultFile.fileLocation.getName())
    
    // Store the instrument configuration
    this._insertInstrumentConfig(resultFile.instrumentConfig.get, storerContext)

    // Retrieve MSISearch and related MS queries
    val msiSearch = resultFile.msiSearch
    val msQueryByInitialId = resultFile.msQueryByInitialId
    val msQueries = if (msQueryByInitialId == null) null else msQueryByInitialId.values.toList.sortBy( _.initialId )

    // Load target result set from result file
    val targetRs = resultFile.getResultSet(false)
    if (StringUtils.isEmpty(targetRs.name)) targetRs.name = msiSearch.title
    
    // Update result set properties
    val rsProps = targetRs.properties.getOrElse(new ResultSetProperties)
    rsProps.setTargetDecoyMode(targetDecoyMode)    
    targetRs.properties = Some(rsProps)
    
    // Update the peaklist software if needed
    if( resultFile.peaklistSoftware.isDefined && targetRs.msiSearch.isDefined ) {
      targetRs.msiSearch.get.peakList.peaklistSoftware = resultFile.peaklistSoftware.get
    }
    
    // Restrieve or create a peaklist writer
    val pklWriter = rsStorer.getOrBuildPeaklistWriter( storerContext )
    
    // Insert the peaklist information
    msiSearch.peakList.id = pklWriter.insertPeaklist(msiSearch.peakList, storerContext)
    
    // Insert spectra contained in result file
    logger.info("Storing spectra...")
    pklWriter.insertSpectra(msiSearch.peakList.id, resultFile, storerContext)

    // Load and store decoy result set if it exists
    if (resultFile.hasDecoyResultSet) {
      
      // Load target result set from result file
      val decoyRs = resultFile.getResultSet(true)
      
      // Link decoy RS to target RS
      targetRs.decoyResultSet = Some(decoyRs)
      
      // Store target and decoy result sets
      rsStorer.storeResultSet(targetRs, msQueries, storerContext)
      
      if(saveSpectrumMatch){
	      // Insert target spectrum matches
	      logger.info("Storing TARGET spectrum matches...")
	      rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)
	      
	      // Insert decoy spectrum matches
	      logger.info("Storing DECOY spectrum matches...")
	      rsStorer.insertSpectrumMatches(decoyRs, resultFile, storerContext)      
      }
      
      logger.info("ResultFile " + resultFile.fileLocation.getName()+" stored in "+(System.currentTimeMillis() - start)/1000.0+" s")
      return targetRs.id
      
    } // Else if a regex has been passed to detect decoy protein matches        
    else if (acDecoyRegex.isDefined) {
      
      // Then split the result set into a target and a decoy one
      val (tRs, dRs) = rsSplitter.get.split(targetRs, acDecoyRegex.get)
      
      // Link decoy RS to target RS
      tRs.decoyResultSet = Some(dRs)
      
      // Store target and decoy result sets
      rsStorer.storeResultSet(tRs, msQueries, storerContext)
      
      logger.debug {
        val targetRSId = if (tRs == null) { 0 } else { tRs.id }
        val decoyRSId = if (dRs == null) { 0 } else { dRs.id }

        "TARGET ResultSet {" + targetRSId + "}  DECOY ResultSet {" + decoyRSId + "}"
      }
      
      // Map peptide matches obtained after split by MS query id and peptide unique key
      val pepMatchMapAfterSplit = Map() ++ (tRs.peptideMatches ++ dRs.peptideMatches).map { pm => 
        (pm.msQuery.id, pm.peptide.uniqueKey) -> pm
      }
      
      // Update peptide match ids of result set provided before split (because peptide matches were cloned during split)
      targetRs.peptideMatches.foreach { pm =>
        pm.id = pepMatchMapAfterSplit( (pm.msQuery.id, pm.peptide.uniqueKey) ).id
      }
      
      if(saveSpectrumMatch){
	      // Insert target and decoy spectrum matches
	      logger.info("Storing target and decoy spectrum matches...")
	      rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)
      }
      
      logger.info("ResultFile " + resultFile.fileLocation.getName()+" stored in "+(System.currentTimeMillis() - start)/1000.0+" s")

      return tRs.id
    }
    else {
      // Store target result set
      rsStorer.storeResultSet(targetRs, msQueries, storerContext)
      
      if(saveSpectrumMatch){
	      // Insert target spectrum matches
	      logger.info("Storing target spectrum matches...")
	      rsStorer.insertSpectrumMatches(targetRs, resultFile, storerContext)
      }
      
      return targetRs.id
    }

  }
  
  /**
   * Insert definition of InstrumentConfig (which should exist in uds) in current MSI db if not already defined
   * Transaction are not managed by this method, should be done by user.
   */
  private def _insertInstrumentConfig(instrumCfg: InstrumentConfig, context: StorerContext) = {
    SQLMsiSearchWriter.insertInstrumentConfig(instrumCfg, context)
  }

}