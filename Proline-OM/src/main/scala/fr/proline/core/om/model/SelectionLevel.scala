package fr.proline.core.om.model

object SelectionLevel {
  val DESELECTED_MANUAL = 0
  val DESELECTED_AUTO = 1
  val SELECTED_AUTO = 2
  val SELECTED_MANUAL = 3
  
  def isSelected(selectionLevel: Int): Boolean = selectionLevel >= 2
  def isDeselected(selectionLevel: Int): Boolean = selectionLevel <= 1
}