package fr.proline.core.om.provider.msi.impl

import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi.MsiDbSpectrumTable
import fr.proline.core.om.model.msi.Spectrum
import fr.proline.core.om.model.msi.SpectrumProperties
import fr.proline.core.om.provider.msi.ISpectrumProvider
import fr.profi.util.bytes._
import fr.profi.util.primitives._

class SQLSpectrumProvider(val msiSqlCtx: DatabaseConnectionContext) extends ISpectrumProvider {

  val spectrumCols = MsiDbSpectrumTable.columns
  
  def getSpectra( spectrumIds: Seq[Long] ): Array[Spectrum] = {
    DoJDBCReturningWork.withEzDBC(msiSqlCtx, { msiEzDBC =>
    	val spectrumIdsAsStr = spectrumIds.mkString(",")
    	val spQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
        	List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ spectrumIdsAsStr ~ ")"
    	)
    	
    	val spectra = msiEzDBC.select(spQuery) { r =>
    	  val peaklistId = toLong(r.getAny(spectrumCols.PEAKLIST_ID))
    	  val instrumentConfigId = toLong(r.getAny(spectrumCols.INSTRUMENT_CONFIG_ID))
    	  
    	  val properties = r.getStringOption(spectrumCols.SERIALIZED_PROPERTIES).map(ProfiJson.deserialize[SpectrumProperties](_))

    	  new Spectrum(
    		  id = toLong(r.getAny(spectrumCols.ID)),
    		  title = r.getString(spectrumCols.TITLE),
    		  precursorMoz = r.getDouble(spectrumCols.PRECURSOR_MOZ),
    		  precursorIntensity = r.getFloat(spectrumCols.PRECURSOR_INTENSITY),
    		  precursorCharge = r.getInt(spectrumCols.PRECURSOR_CHARGE),
    		  isSummed = r.getBoolean(spectrumCols.IS_SUMMED),
    		  firstCycle = r.getInt(spectrumCols.FIRST_CYCLE),
    		  lastCycle = r.getInt(spectrumCols.LAST_CYCLE),
    		  firstScan = r.getInt(spectrumCols.FIRST_SCAN),
    		  lastScan = r.getInt(spectrumCols.LAST_SCAN),
    		  firstTime = r.getFloat(spectrumCols.FIRST_TIME),
    		  lastTime = r.getFloat(spectrumCols.LAST_TIME),
    		  mozList = bytesTodoublesOption(r.getBytesOption(spectrumCols.MOZ_LIST)),
    		  intensityList = bytesTofloatsOption(r.getBytesOption(spectrumCols.INTENSITY_LIST)),
    		  peaksCount = r.getInt(spectrumCols.PEAK_COUNT),
    		  instrumentConfigId = instrumentConfigId,
    		  peaklistId = peaklistId
    	  )
    	}
    	spectra.toArray
    }, false)  
  }
  
}