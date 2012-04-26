package fr.proline.core.service.msi

import fr.proline.core.service.IService

// Fake InstrumentConfigProvider which should be a JPA object
trait ProviderFake {
  def get( id: Int ) = new Object
}

class ResultFileFake {
  def hasDecoyResultSet = true
  def getResultSet( wantDecoy: Boolean ) = new Object
}
trait ResultFileProviderFake {
  def getResultFile( filePath: String, fileType: String ) = new ResultFileFake
}
trait ResultSetStorerFake {
  def storeResultSet( resultSet: Object ) = new ResultFileFake
}


class ResultFileImporterFake( projectId: Int,
                              filePath: String,
                              fileType: String,
                              instrumentConfigId: Int,
                              specTitleRuleId: Int,
                              peaklistId: Int = 0
                             ) extends IService {
  
  val instrumentConfigProvider = new Object with ProviderFake
  val specTitleParsingRuleProvider = new Object with ProviderFake
  val resultFileProvider = new Object with ResultFileProviderFake
  val resultSetStorer = new Object with ResultSetStorerFake
  
  val instrumentConfig = instrumentConfigProvider.get( instrumentConfigId )
  val specTitleParsingRule = specTitleParsingRuleProvider.get( specTitleRuleId )
  if( specTitleParsingRule == null ) {
    throw new Exception("undefined parsing rule with id=" + specTitleRuleId )
  }
  
  val resultFile = resultFileProvider.getResultFile( filePath, fileType )
  
  def runService(): Boolean = {
        
    // Store MSI search related information (parameters, MS queries, peaklist)
    val jpaMsiSearchRecord = this.storeMsiSearchData()
    
    var jpaDecoyRsRecord: Object = null
    if( resultFile.hasDecoyResultSet ) {
      val decoyRs = resultFile.getResultSet( wantDecoy = true );
      //decoyRs.name = msiSearch.title
      //decoyRs.msiSearchIds = msiSearch.id
      
      jpaDecoyRsRecord = resultSetStorer.storeResultSet( decoyRs );
    }
    
    // Prepare the target result container for storage
    val resultSet = resultFile.getResultSet( wantDecoy = false );
    //resultSet.name = msiSearch.title
    //resultSet.msiSearchIds = msiSearch.id
    
    val jpaResultSetRecord = resultSetStorer.storeResultSet( resultSet );

    if( jpaDecoyRsRecord != null ) {
      //jpaResultSetRecord.decoyResultSetId = decoyRs.id
      //jpaResultSetRecord.save
    }
    
    // Map the target result set to the MSI search
    //jpaMsiSearchRecord.targetResultSetId = jpaResultSetRecord.id
    //jpaMsiSearchRecord.save;
    
    true
  }
  
  def storeMsiSearchData(): Object = {
    new Object()
  }
  
}