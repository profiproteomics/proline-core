package fr.proline.core.om.provider.msi.impl

import fr.profi.chemistry.model.Enzyme
import fr.profi.chemistry.model.EnzymeCleavage
import fr.profi.jdbc.easy.EasyDBC
import fr.profi.util.primitives._
import fr.proline.context._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.tables.uds._
import fr.proline.core.om.builder.EnzymeBuilder._
import fr.proline.core.om.builder.MsiSearchBuilder._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.provider.msi.IMSISearchProvider

class SQLMsiSearchProvider(
  val udsDbCtx: UdsDbConnectionContext,
  val msiDbCtx: MsiDbConnectionContext
) extends IMSISearchProvider {
  
  import SQLInstrumentConfigProvider._
  import SQLMsiSearchProvider._
  import SQLPeaklistProvider._
  import SQLPeaklistSoftwareProvider._
  
  protected lazy val ptmProvider = new SQLPTMProvider(msiDbCtx)

  def getMSISearches(msiSearchIds: Seq[Long]): Array[MSISearch] = {
    if( msiSearchIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>

        val msiSearchQuery = new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (t,c) =>
          List(t.*) -> "WHERE "~ t.ID ~" IN("~ msiSearchIds.mkString(",") ~")"
        )
        
        buildMsiSearches(
          msiEzDBC.select(msiSearchQuery),
          peaklistIds => selectPeaklistRecords(msiEzDBC, peaklistIds),
          pklSoftIds => selectPklSoftRecords(msiEzDBC, pklSoftIds),
          searchSettingsIds => selectAndMapSearchSettingsRecords(msiEzDBC,searchSettingsIds),
          searchSettingsIds => selectPMFSearchRecords(msiEzDBC,searchSettingsIds),
          searchSettingsIds => selectMSMSSearchRecords(msiEzDBC,searchSettingsIds),
          searchSettingsId => selectAndMapSearchedSeqDbRecords(msiEzDBC, searchSettingsId),
          searchSettingsIds => selectUsedEnzymeRecords(msiEzDBC,searchSettingsIds),
          searchSettingsIds => selectUsedPtmRecords(msiEzDBC,searchSettingsIds),
          enzymeIds => selectAndMapEnzymeRecords(msiEzDBC, enzymeIds),
          enzymeId => selectAndMapEnzymeCleavageRecords(udsEzDBC, enzymeId),
          instConfigIds => selectInstConfigRecords(udsEzDBC, instConfigIds),
          instIds => selectInstrumentRecords(udsEzDBC, instIds),
          fragRuleSetsIds => selectFragRuleSetsRecords(udsEzDBC, fragRuleSetsIds),
          fragRuleSetsIds => selectFragmentationSeriesRecords(udsEzDBC, fragRuleSetsIds),
          ptmProvider
        )
        
      }
    }
  }

  def getMSISearchesAsOptions(msiSearchIds: Seq[Long]): Array[Option[MSISearch]] = {
    val msiSearches = this.getMSISearches(msiSearchIds)
    val msiSearchById = msiSearches.map { s => s.id -> s } toMap

    msiSearchIds.map { msiSearchById.get(_) } toArray
  }

  def getSearchSettingsList(searchSettingsIds: Seq[Long]): Array[SearchSettings] = {
    if( searchSettingsIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
  
        buildSearchSettingsList(
          selectAndMapSearchSettingsRecords(msiEzDBC,searchSettingsIds),
          selectPMFSearchRecords(msiEzDBC,searchSettingsIds),
          selectMSMSSearchRecords(msiEzDBC,searchSettingsIds),
          selectUsedEnzymeRecords(msiEzDBC,searchSettingsIds),
          selectUsedPtmRecords(msiEzDBC,searchSettingsIds),
          enzymeIds => selectAndMapEnzymeRecords(msiEzDBC, enzymeIds),
          enzymeId => selectAndMapEnzymeCleavageRecords(udsEzDBC, enzymeId),
          instConfigIds => selectInstConfigRecords(udsEzDBC, instConfigIds),
          instIds => selectInstrumentRecords(udsEzDBC, instIds),
          fragRuleSetsIds => selectFragRuleSetsRecords(udsEzDBC, fragRuleSetsIds),
          instConfigIds => selectFragmentationSeriesRecords(udsEzDBC, instConfigIds),
          searchSettingsId => selectAndMapSearchedSeqDbRecords(msiEzDBC, searchSettingsId),
          ptmProvider
        )
        
      }
    }
  }

  def getSeqDatabases(seqDbIds: Seq[Long]): Array[SeqDatabase] = {
    if( seqDbIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
    
      val seqDbQuery = new SelectQueryBuilder1(MsiDbSeqDatabaseTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.ID ~" IN("~ seqDbIds.mkString(",") ~")"
      )
      
      buildSeqDatabases( msiEzDBC.select(seqDbQuery) )    
    }
  }
  
  def getSearchedSeqDatabases(searchSettingsId: Long): Array[SeqDatabase] = {    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msiEzDBC =>
      buildSearchedSeqDatabases( selectAndMapSearchedSeqDbRecords(msiEzDBC,searchSettingsId) )      
    }
  }

  def getEnzymesByName(enzymeNames: Seq[String]): Array[Enzyme] = {
    if( enzymeNames.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
  
      val quotedEnzymeNames = enzymeNames.map(udsEzDBC.dialect.quoteString(_))
      val enzQuery = new SelectQueryBuilder1(UdsDbEnzymeTable).mkSelectQuery( (t,c) =>
        List(t.*) -> "WHERE "~ t.NAME ~" IN("~ quotedEnzymeNames.mkString(",") ~")"
      )
      
      buildEnzymes(
        udsEzDBC.select(enzQuery),
        enzymeId => selectAndMapEnzymeCleavageRecords( udsEzDBC, enzymeId )
      )
    
    }
  }
  
  def getEnzymes(enzymeIds: Seq[Long]): Array[Enzyme] = {
    if( enzymeIds.isEmpty ) return Array()
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      buildEnzymes(
        selectAndMapEnzymeRecords( udsEzDBC, enzymeIds ),
        enzymeId => selectAndMapEnzymeCleavageRecords( udsEzDBC, enzymeId )
      )
    }
  }
  
  private def getEnzymeCleavages(enzymeId: Long): Array[EnzymeCleavage] = {
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      buildEnzymeCleavages( selectAndMapEnzymeCleavageRecords(udsEzDBC,enzymeId) )
    }
  }
  
  def getAllEnzymes(): Array[Enzyme] = {
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      buildEnzymes( 
        selectAndMapAllEnzymeRecords(udsEzDBC),
        enzymeId => selectAndMapEnzymeCleavageRecords( udsEzDBC, enzymeId )
      )
    }
  }

}

object SQLMsiSearchProvider {
  
  def selectAndMapSearchSettingsRecords( msiEzDBC: EasyDBC, ssIds: Seq[Long] ): (IValueContainer => SearchSettings) => Seq[SearchSettings] = {
    
    val ssIdsStr = ssIds.mkString(",")

    val ssQuery = new SelectQueryBuilder1(MsiDbSearchSettingsTable).mkSelectQuery( (t,c) =>
      List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
    )
    
    msiEzDBC.select(ssQuery)
  }
  
  def selectPMFSearchRecords( msiEzDBC: EasyDBC, ssIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    val ssIdsStr = ssIds.mkString(",")

    // Retrieve PMF search settings
    val pmfSearchQuery = new SelectQueryBuilder1( MsiDbIonSearchTable ).mkSelectQuery( (t,c) =>
      List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
    )
    
    msiEzDBC.selectAndProcess(pmfSearchQuery)
  }
  
  def selectMSMSSearchRecords( msiEzDBC: EasyDBC, ssIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    val ssIdsStr = ssIds.mkString(",")

    // Retrieve MS/MS search settings
    val msmsSearchQuery = new SelectQueryBuilder1( MsiDbMsmsSearchTable ).mkSelectQuery( (t,c) =>
      List(t.*) -> " WHERE " ~ t.ID ~ " IN (" ~ ssIdsStr ~ ")"
    )
    
    msiEzDBC.selectAndProcess(msmsSearchQuery)
  }
  
  def selectUsedEnzymeRecords( msiEzDBC: EasyDBC, ssIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    val ssIdsStr = ssIds.mkString(",")

    val usedEnzQuery = new SelectQueryBuilder1(MsiDbUsedEnzymeTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.SEARCH_SETTINGS_ID ~" IN("~ ssIdsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(usedEnzQuery)
  }
  
  def selectUsedPtmRecords( msiEzDBC: EasyDBC, ssIds: Seq[Long] ): (IValueContainer => Unit) => Unit = {
    
    val ssIdsStr = ssIds.mkString(",")

    val usedPtmQuery = new SelectQueryBuilder1(MsiDbUsedPtmTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.SEARCH_SETTINGS_ID ~" IN("~ ssIdsStr ~")"
    )
    
    msiEzDBC.selectAndProcess(usedPtmQuery)
  }
  
  def selectAndMapSearchedSeqDbRecords(msiEzDBC: EasyDBC, searchSettingsId: Long): (IValueContainer => SeqDatabase) => Seq[SeqDatabase] = {
    
    val sqb2 = new SelectQueryBuilder2[MsiDbSeqDatabaseColumns.type,MsiDbSearchSettingsSeqDatabaseMapColumns.type ](MsiDbSeqDatabaseTable, MsiDbSearchSettingsSeqDatabaseMapTable)
    
    val sqlQuery = sqb2.mkSelectQuery( (t1, c1, t2, c2) =>
      List(t1.*,t2.SEARCHED_SEQUENCES_COUNT,t2.SERIALIZED_PROPERTIES) ->
      " WHERE " ~ t1.ID ~ " = " ~ t2.SEQ_DATABASE_ID ~
      " AND " ~ t2.SEARCH_SETTINGS_ID ~ " = " ~ searchSettingsId
    )
    
    msiEzDBC.select(sqlQuery)
  }
  
  def selectAndMapEnzymeRecords(ezDBC: EasyDBC, enzymeIds: Seq[Long]): (IValueContainer => Enzyme) => Seq[Enzyme] = {
    
    val enzQuery = new SelectQueryBuilder1(UdsDbEnzymeTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ enzymeIds.mkString(",") ~")"
    )
    
    ezDBC.select(enzQuery)
  }
  
  def selectAndMapAllEnzymeRecords(udsEzDBC: EasyDBC): (IValueContainer => Enzyme) => Seq[Enzyme] = {
    
    val enzQuery = new SelectQueryBuilder1(UdsDbEnzymeTable).mkSelectQuery( (t,c) =>
      List(t.*) -> ""
    )
    
    udsEzDBC.select(enzQuery)
  }
  
  def selectAndMapEnzymeCleavageRecords(udsEzDBC: EasyDBC, enzymeId: Long): (IValueContainer => EnzymeCleavage) => Seq[EnzymeCleavage] = {
    
    val enzQuery = new SelectQueryBuilder1(UdsDbEnzymeCleavageTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ENZYME_ID ~" = "~ enzymeId
    )
    
    udsEzDBC.select(enzQuery)
  }
  
}
