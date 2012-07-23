package fr.proline.core.utils.generator

import scala.util.Random

object Randomator {

    val accessionChars = ('A' to 'Z') ++ ('0' to '9')
	val commonAA = "ANDCEQGHILMFPSTWYV" //Common amino acid residues (peptide sequence)
	val trypsicAA = "RK" //trypsic amino acid
	  
	//Sequence size  
	val minSeqSize:Int = 8
	val maxSeqSize:Int = 20
	  
	//score
	val minPepMatchScore:Float = 20.0f
	val maxPepMatchScore:Float = 120.0f
	
	
    def aaSequence():String = { aaSequence(minSeqSize, maxSeqSize) } 
	def aaSequence(lengthMin:Int, lengthMax:Int):String = {
	  randomString(commonAA, lengthMin, lengthMax-1)+randomString(trypsicAA, 1, 1)	  	  
	} 
	def matchScore():Float = {
	  randomDouble(minPepMatchScore, maxPepMatchScore).asInstanceOf[Float]
	}
	def protAccession():String = {	 
	  randomString(chars=accessionChars.toString, lengthMin=8, lengthMax=12)
	}
	def pepCharge():Int = {
	  randomInt(1, 3)
	}	
	
	/**
	 * Generic methods
	 */  	
	private def randomString(chars:String, lengthMin:Int, lengthMax:Int) : String = {	  	
	    val length = randomInt(lengthMin,lengthMax)
  		val newKey = (1 to length).map(
  			x => {
  				val index = Random.nextInt(chars.length)
	            chars(index)
  			}
  		).mkString("")	 
  		newKey
	}
	
	private def randomInt(minInclu:Int, maxInclu:Int) : Int = {	  	  
	  require(minInclu<=maxInclu)
	  if (minInclu == maxInclu)
	    minInclu
	    else
	      Random.nextInt(maxInclu+1-minInclu)+minInclu
	}
	
	private def randomDouble(minInclu:Double, maxExclu:Double) : Double = {
	  require(minInclu<=maxExclu)
	  if (minInclu == maxExclu)
	    minInclu
	    else
	      Random.nextDouble()*(maxExclu-minInclu)+minInclu
	}
}
	
