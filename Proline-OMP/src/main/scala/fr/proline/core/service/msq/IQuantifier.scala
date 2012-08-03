package fr.proline.core.service.msq

import fr.proline.core.dal.MsiDb
import fr.proline.core.orm.uds.QuantitationFraction
import fr.proline.core.dal.helper.MsiDbHelper

trait IQuantifier {
  
  // Required fields
  val msiDb: MsiDb
  val udsQuantFraction: QuantitationFraction
  
  // Instantiated fields
  lazy val msiDbHelper = new MsiDbHelper( msiDb )
  
  def quantify(): Unit
  
}