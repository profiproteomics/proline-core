package fr.proline.core.service

import com.weiglewilczek.slf4s.Logging

trait IService extends Runnable with HasProgress {
  
  protected def beforeInterruption() = {}
  
  def runService() : Boolean
  
  // Define a run method which implements the Thread interruption policy
  def run(): Unit = {
    
    this.registerOnProgressAction( Thread.sleep(1) )
    
    try {
      this.runService()
    } catch {
      case ie: InterruptedException => {
        this.beforeInterruption()
        Thread.currentThread.interrupt() // very important to interrupt again
      }
      case e: Exception => throw e
    }
    
  }  
    
}

trait HasProgress extends Logging {
  
  case class Step
  
  import scala.collection.mutable.ArrayBuffer
  
  //val progressPlan: Array[Step]
  
  private val progress = 0f
  private val onProgressActions = new ArrayBuffer[Function0[Unit]](0)
  //private var onStepActions = new Array[ArrayBuffer[Function0[Unit]]](progressPlan.length)
  
  protected def >>>(): Unit = {
    
    // Execute all registered actions
    for( onProgressAction <- onProgressActions ) {
      onProgressAction()
    }
    
    // TODO: execute step actions
    
  }
  
  def registerOnProgressAction( action: => Unit ): Unit = this.onProgressActions += { () => action }
  
  //def registerOnStepAction( stepNum: Int, action: () => Unit ): Unit = this.onStepActions(stepNum-1) += action
  
}