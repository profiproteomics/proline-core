package fr.proline.core.om.model.pdi

import scala.reflect.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

case class SeqDbEntryProperties (
  @BeanProperty var acNumbers: Array[String],
  @BeanProperty var isDeleted: Option[Boolean] = None,
  @BeanProperty var isDemerged: Option[Boolean] = None,
  @BeanProperty var isMerged: Option[Boolean] = None,
  @BeanProperty var isSequenceUpdated: Option[Boolean] = None,
  @BeanProperty var isTaxonUpdated: Option[Boolean] = None,  
  @BeanProperty var newAcNumbers: Option[Array[String]] = None,
  @BeanProperty var newIdentifiers: Option[Array[String]] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  @BeanProperty var newBioSequenceId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  @BeanProperty var newTaxonId: Option[Long] = None
)