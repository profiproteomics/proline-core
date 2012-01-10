package fr.proline.core.om.factory

import fr.proline.core.om.msi.PeptideClasses.PeptideMatch

trait IPeptideMatchLoader {
  
 def getPeptideMatch(pepMatchId:Int):PeptideMatch
 
 def PeptideMatchesById( pepMatchIds: Seq[Int] ):Seq[PeptideMatch]
 
 def PeptideMatchesForResultSet( resultSetId: Int ):Seq[PeptideMatch]
}