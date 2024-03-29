package fr.proline.core.util.generator.msi

import scala.util.Random
import fr.profi.util.random._

object ResultSetRandomator {
  
  val accessionChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  val commonAA = "ANDCEQGHILMFPSTWYV" //Common amino acid residues (peptide sequence)
  val trypsicAA = "RK" //trypsic amino acid
  val trypsicRegex = (trypsicAA.toArray.mkString("|") ).r

  // Sequence size  
  val minSeqSize: Int = 8
  val maxSeqSize: Int = 20

  // score
  val minPepMatchScore: Float = 20.0f
  val maxPepMatchScore: Float = 120.0f

  def randomAASequence(): String = { randomAASequence(minSeqSize, maxSeqSize) }

  def randomAASequence(lengthMin: Int, lengthMax: Int): String = {
    randomString(commonAA, lengthMin, lengthMax - 1) + randomString(trypsicAA, 1, 1)
  }

  def randomPepMatchScore(): Float = {
    randomDouble(minPepMatchScore, maxPepMatchScore).asInstanceOf[Float]
  }

  def randomProtAccession(): String = {
    randomString(chars = accessionChars, lengthMin = 8, lengthMax = 12)
  }

  def randomPepCharge(): Int = {
    randomInt(1, 3)
  }

}
	
