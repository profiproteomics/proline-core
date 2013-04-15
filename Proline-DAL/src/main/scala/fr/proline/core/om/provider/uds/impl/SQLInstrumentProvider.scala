package fr.proline.core.om.provider.uds.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.provider.msi.IInstrumentProvider
import fr.proline.util.primitives._
    
class SQLInstrumentProvider(val udsDbCtx: DatabaseConnectionContext) extends IInstrumentProvider {
  
  def getInstrumentsAsOptions( instrumentIds: Seq[Int] ): Array[Option[Instrument]] = {
    null
  }
  
  def getInstruments( instrumentIds: Seq[Int] ): Array[Instrument] = {
    null
    
  }

}
