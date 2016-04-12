package fr.proline.core.om.storer.msi

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.orm.msi.{ Peptide => MsiPeptide }
import fr.proline.core.orm.msi.{ PeptideInstance => MsiPeptideInstance }
import fr.proline.core.orm.msi.{ PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap }
import fr.proline.core.orm.msi.{ PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK }
import fr.proline.core.orm.msi.{ PeptideMatch => MsiPeptideMatch }
import fr.proline.core.orm.msi.{ PeptideMatchRelation => MsiPeptideMatchRelation }
import fr.proline.core.orm.msi.{ PeptideMatchRelationPK => MsiPeptideMatchRelationPK }
import fr.proline.core.orm.msi.{ PeptideReadablePtmString => MsiPeptideReadablePtmString }
import fr.proline.core.orm.msi.{ PeptideReadablePtmStringPK => MsiPeptideReadablePtmStringPK }
import fr.proline.core.orm.msi.{ PeptideSet => MsiPeptideSet }
import fr.proline.core.orm.msi.{ PeptideSetPeptideInstanceItem => MsiPeptideSetItem }
import fr.proline.core.orm.msi.{ PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK }
import fr.proline.core.orm.msi.{ PeptideSetProteinMatchMap => MsiPepSetProtMatchMap }
import fr.proline.core.orm.msi.{ PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK }
import fr.proline.core.orm.msi.{ ProteinMatch => MsiProteinMatch }
import fr.proline.core.orm.msi.{ ProteinSet => MsiProteinSet }
import fr.proline.core.orm.msi.{ ProteinSetProteinMatchItem => MsiProtSetProtMatchItem }
import fr.proline.core.orm.msi.{ ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK }
import fr.proline.core.orm.msi.{ ResultSet => MsiResultSet }
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.msi.{ Scoring => MsiScoring }
import fr.proline.core.orm.msi.{ SequenceMatch => MsiSequenceMatch }
import fr.proline.core.util.ResidueUtils.scalaCharToCharacter
import javax.persistence.EntityManager
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.model.msi.LazyResultSummary

trait IRsmDuplicator {

  /**
   * Clone source ResultSummary objects and attach them to empty RSM/TS
   */
  def cloneAndStoreRSM(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet, msiEm: EntityManager): ResultSummary
  

}

