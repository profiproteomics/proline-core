package fr.proline.core.om.model.pdi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SeqDbEntryProperties (
  @BeanProperty var acNumbers: Array[String],
  @BeanProperty var isDeleted: Option[Boolean] = None,
  @BeanProperty var isDemerged: Option[Boolean] = None,
  @BeanProperty var isMerged: Option[Boolean] = None,
  @BeanProperty var isSequenceUpdated: Option[Boolean] = None,
  @BeanProperty var isTaxonUpdated: Option[Boolean] = None,  
  @BeanProperty var newAcNumbers: Option[Array[String]] = None,
  @BeanProperty var newIdentifiers: Option[Array[String]] = None,
  @BeanProperty var newBioSequenceId: Option[Long] = None,
  @BeanProperty var newTaxonId: Option[Long] = None
)