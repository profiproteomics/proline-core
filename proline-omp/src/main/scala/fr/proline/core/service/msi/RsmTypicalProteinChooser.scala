package fr.proline.core.service.msi

import com.typesafe.scalalogging.LazyLogging

import fr.profi.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.TypicalProteinChooser
import fr.proline.core.algo.msi.TypicalProteinChooserRule

class RsmTypicalProteinChooser(
  execCtx: IExecutionContext,
  resultSummaryId: Long,
  rulesToApplyPrioritized: Seq[TypicalProteinChooserRule]
) extends IService with LazyLogging {

  require(execCtx.isJPA, " Invalid connexion type for this service ")
  require(resultSummaryId > 0L, "Invalid  ResultSummary Id specified")
  private var modifiedProteinSetsCount = 0

  def runService(): Boolean = {

    val msiDbContext = execCtx.getMSIDbConnectionContext
    msiDbContext.beginTransaction()
    val msiEM = msiDbContext.getEntityManager

    logger.info("Run Typical Protein Chooser")

    val typicalChooser = new TypicalProteinChooser()
    typicalChooser.changeTypical(resultSummaryId, rulesToApplyPrioritized, msiEM)

    logger.info("Save data for Typical Protein Chooser")

    val changedPS = typicalChooser.getChangedProteinSets
    modifiedProteinSetsCount = changedPS.size
    changedPS.foreach(msiEM.merge(_))

    // Commit transaction if it was initiated locally
    msiDbContext.commitTransaction()
    true
  }

  def getChangedProteinSetsCount: Int = { modifiedProteinSetsCount }

}