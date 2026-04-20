package fr.proline.core.service.msq.quantify

import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.ExperimentalDesign
import fr.proline.core.orm.uds.MasterQuantitationChannel

abstract class AbstractDiannQuantifier (val executionContext: IExecutionContext,
                                val udsMasterQuantChannel: MasterQuantitationChannel,
                                val experimentalDesign: ExperimentalDesign
                              ) extends AbstractMasterQuantChannelQuantifier {


}
