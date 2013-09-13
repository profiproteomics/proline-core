package fr.proline.core.algo.msi

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.ContextFactory
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.msi.IResultSetProvider

class TypicalProteinChooserTest extends AbstractMultipleDBTestCase with Logging {
  // Define the interface to be implemented
  val driverType = DriverType.H2
  val fileName = "Merged_RSM_Test"
  val targetRSMId: Long = 2

  var executionContext: IExecutionContext = null
  var rsmProvider: IResultSummaryProvider = null
  var rsProvider: IResultSetProvider = null
  protected var readRSM: ResultSummary = null

  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing DBs")
    super.initDBsDBManagement(driverType)

    //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/ps-db.xml")
    msiDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")

    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    val (execContext, rsmProv, rsProv) = buildSQLContext()
    executionContext = execContext
    rsmProvider = rsmProv
    rsProvider = rsProv
    readRSM = this._loadRSM()
    this._loadRSFor(readRSM)
  }

  private def _loadRSM(): ResultSummary = {
    val rsm = rsmProvider.getResultSummary(targetRSMId,false).get
	return rsm
  }
  
  private def _loadRSFor(rsm: ResultSummary) = {
    val rs = rsProvider.getResultSet(rsm.getResultSetId)
    rsm.resultSet = rs	
  }
  
  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }

  def buildSQLContext() = {
    val udsDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getUdsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val pdiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector, true)
    val psDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getPsDbConnector, false).asInstanceOf[SQLConnectionContext]
    val msiDbCtx = ContextFactory.buildDbConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(1), false).asInstanceOf[SQLConnectionContext]
    val executionContext = new BasicExecutionContext(udsDbCtx, pdiDbCtx, psDbCtx, msiDbCtx, null)

    val rsmProvider = new SQLResultSummaryProvider(msiDbCtx, psDbCtx, udsDbCtx)
    val rsProvider = new SQLResultSetProvider(msiDbCtx, psDbCtx, udsDbCtx)

    (executionContext, rsmProvider, rsProvider)
  }

  
  @Test
  def testChangeTypicalProt() = {
	
	val pmByIds = readRSM.resultSet.get.proteinMatchById
	
	//Load proteinMatches in all ProteinSet !!	
	readRSM.proteinSets.foreach(protSet=> {
	   var protSetProtMatches = Array.newBuilder[ProteinMatch]
	   protSet.getProteinMatchIds.foreach(id =>{
          protSetProtMatches += pmByIds(id) 
	   })
	   protSet.proteinMatches = Some(protSetProtMatches.result)
	   protSet.setTypicalProteinMatch(pmByIds(protSet.getTypicalProteinMatchId))
	})

	//Verify which proteinSets should be modified by algo
	var nbrTremblTPM:Int = 0    // Count proteinSet with Trembl typical protein match
	var nbrTremblShouldChange : Int = 0 // Count proteinSet with Trembl typical protein match but which have "sp" proteinmatch in their sameset 
	
    readRSM.proteinSets.foreach(protSet=> {
      
      val sameSetPMIds =  protSet.peptideSet.proteinMatchIds //Change only with samese proteinMatch

      if(pmByIds(protSet.getTypicalProteinMatchId).description.startsWith("tr")) {
        nbrTremblTPM += 1
       
        if(protSet.proteinMatches.get.filter(pm=>{ (pm.description.startsWith("sp") && sameSetPMIds.contains(pm.id) )}).length>0) 
          nbrTremblShouldChange += 1
      }
    })    
	
    val typicalChooser = new TypicalProteinChooser()
	val ruleDesc = new TypicalProteinChooserRule(ruleName="Sp chooser", applyToAcc=false,rulePattern="^sp.*" )
	typicalChooser.changeTypical(readRSM,ruleDesc,false)
	
	var newNbrTremblTPM:Int = 0    
	readRSM.proteinSets.foreach(protSet=> {
      if(protSet.getTypicalProteinMatch.get.description.startsWith("tr")) {	   
        newNbrTremblTPM += 1        
      }
    })
    Assert.assertEquals(nbrTremblTPM-nbrTremblShouldChange, newNbrTremblTPM)
	
  }
  
  
}
