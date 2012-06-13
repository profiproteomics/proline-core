package fr.proline.core.service.msi

import java.io.File
import scala.collection.mutable.ArrayBuffer
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.{MsiDb,PsDb,UdsDb}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.storer.msi.{MsiSearchStorer,PeaklistStorer,RsStorer}
import fr.proline.core.service.IService
import fr.proline.core.algo.msi._
import fr.proline.core.algo.msi.validation._
import fr.proline.core.om.storer.msi.RsmStorer

/*
case class ComputerValidationParams( wantedFdr: Double, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None )
case class UserValidationParams( pValue: Float, minPepSeqLength: Int = 0, properties: Option[Map[String,Any]] = None )
*/
class ResultSetValidator( projectId: Int,
                          targetRsId: Int,
                          decoyRsId: Option[Int] = None,
                          pepMatchValParams: Option[ValidationParams] = None,
                          protSetValParams: Option[ValidationParams] = None,
                          targetDecoyMode: Option[TargetDecoyModes.Mode] = None,
                          storeResultSummary: Boolean = true ) extends IService with Logging {
  
  private val msiDb = new MsiDb( MsiDb.getDefaultConfig ) // TODO: retrieve from UDS-DB
  private val psDb = new PsDb( PsDb.getDefaultConfig ) // TODO: retrieve from UDS-DB
  private val udsDb = new UdsDb( UdsDb.getDefaultConfig ) // TODO: retrieve from config
  
  var validatedTargetRsm: ResultSummary = null
  
  def runService(): Boolean = {
    
    import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
    
    val startTime = curTimeInSecs()
    
    // Load target result set
    val rsProvider = new SQLResultSetProvider( msiDb, psDb )
    val targetRsOpt = rsProvider.getResultSet( targetRsId )
    if( targetRsOpt == None )
      throw new Exception("can't load result set with id = " + targetRsId )
    
    // Load decoy result set if it exists
    val targetRs = targetRsOpt.get
    val decoyRsOpt = if( decoyRsId != None ) rsProvider.getResultSet( decoyRsId.get ) else None

    // Retrieve search engine name    
    val searchEngine = this._getSearchEngineName( targetRs )
    
    // Instantiate a peptide match validator
    val pepMatchValidator = PeptideMatchValidator( searchEngine )
    
    // Create RSM validation properties
    val rsmValProperties = RsmValidationProperties (
      params = RsmValidationParamsProperties(),
      results = RsmValidationResultsProperties()    
    )
    
    // Run peptide match validation
    pepMatchValParams match {
      case computerPepMatchValParamsOpt: Some[ComputerValidationParams] => {
        val computerPepMatchValParams = computerPepMatchValParamsOpt.get
        
        // Set computer params
        val rsmPepValParams = new RsmPepMatchValidationParamsProperties(
                                     expectedFdr = Some(computerPepMatchValParams.expectedFdr),
                                     scoreThreshold = None
                                  )
        rsmValProperties.params.setPeptideParams( Some(rsmPepValParams) )
        
        // Validate thre Result Set
        val valResults = pepMatchValidator.validateWithComputerParams(
                           validationParams = computerPepMatchValParams,
                           targetPeptideMatches = targetRs.peptideMatches,
                           decoyPeptideMatches = decoyRsOpt.get.peptideMatches
                           )
        val expectedResult = valResults.expectedResult
        
        // Keep validation results at peptide level
        val pepValResults = RsmPepMatchValidationResultsProperties(
              pValueThreshold = valResults.expectedResult.properties.get("p_value").asInstanceOf[Double].toFloat,
              targetMatchesCount = expectedResult.nbTargetMatches,
              decoyMatchesCount = expectedResult.nbDecoyMatches,
              fdr = expectedResult.fdr
            )
        rsmValProperties.results.setPeptideResults( Some(pepValResults) )
      }
      case userPepMatchValParamsOpt: Some[UserValidationParams] => {
        val userPepMatchValParams = userPepMatchValParamsOpt.get
        
        // Set user params
        val rsmPepValParams = new RsmPepMatchValidationParamsProperties(
                                     //expectedFdr = userPepMatchValParams.get.,
                                     //scoreThreshold = None
                                  )
        rsmValProperties.params.setPeptideParams( Some(rsmPepValParams) )
        
        val valResult = pepMatchValidator.validateWithUserParams(
                          validationParams = userPepMatchValParams,
                          targetPeptideMatches = targetRs.peptideMatches,
                          decoyPeptideMatches = Some(decoyRsOpt.get.peptideMatches),
                          targetDecoyMode = targetDecoyMode
                          )
      }
      case _ => ()
    }
    
    // Update selection levels 
    
    // Instantiate a protein set inferer
    val protSetInferer = ProteinSetInferer( InferenceMethods.parsimonious )
    
    val resultSets = List( Some(targetRs), decoyRsOpt )
    val resultSummaries = List.newBuilder[Option[ResultSummary]]
    
    //Build result summary for each individual result set
    for( rs <- resultSets ) {
      if( rs != None ) {
        // Create new result set with validated peptide matches and compute result summary
        val validatedRs = this._copyRsWithValidatedPepMatches( rs.get )      
        val rsm = protSetInferer.computeResultSummary( validatedRs )
        resultSummaries += Some(rsm)
      }
    }
    
    // Build the list of validated RSMs
    val rsmList = resultSummaries.result()
    
    // Retrieve target/decoy RSMs
    val targetRsm = rsmList(0).get
    val decoyRsmOpt = rsmList(1)
    
    // Set target RSM validation properties
    targetRsm.properties = Some( ResultSummaryProperties( validationProperties = Some(rsmValProperties) ) )
    
    // Instantiate a protein set validator
    val protSetValidator = ProteinSetValidator( searchEngine, ValidationMethods.proteinSetScore )
    
    // Run protein set validation
    protSetValParams match {
      case computerProtSetValParams: Some[ComputerValidationParams] => {
         val valResults = protSetValidator.validateWithComputerParams(
                            validationParams = computerProtSetValParams.get,
                            targetRsm = targetRsm,
                            decoyRsm = decoyRsmOpt.get
                            )
      }
      /*case userProtSetValParams: Option[UserValidationParams] => {
        val valResult = protSetValidator.validateWithComputerParams()
      }
      case none: Option[ValidationParams] => ()*/
      case _ => ()
    }
    
    // Select only validated proteins
    for( rsm <- rsmList ) {
      for( proteinSet <- rsm.get.proteinSets ) {
        if( proteinSet.isValidated ) {
          proteinSet.selectionLevel = 2
        } else {
          proteinSet.selectionLevel = 1
        }
      }
    }
    
    val took = startTime - curTimeInSecs()
    this.logger.info( "validation took "+ took +" seconds")
      
    if( storeResultSummary ) {
      
      // Instantiate a RSM storer
      val rsmStorer = RsmStorer( msiDb )
      
      // Store decoy result summary
      if( decoyRsmOpt != None ) {
        rsmStorer.storeResultSummary( decoyRsmOpt.get )
        targetRsm.decoyResultSummary = decoyRsmOpt
      }
      
      // Store target result summary
      rsmStorer.storeResultSummary( targetRsm )
      
      // Commit transaction
      this.msiDb.commitTransaction()
      
      this.logger.info( "result summary successfully stored !")
    }
    
    // Update the service results
    this.validatedTargetRsm = targetRsm
    
    true
  }
  
  // TODO: retrieve the name from the MSI search
  private def _getSearchEngineName( rs: ResultSet ): String = "mascot"
    
  private def _copyRsWithValidatedPepMatches( rs: ResultSet ): ResultSet = {
    rs.copy( peptideMatches = rs.peptideMatches.filter { _.isValidated } )
  }
  
  def curTimeInSecs() = System.currentTimeMillis()/1000
   
}