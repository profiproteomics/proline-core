package fr.proline.core.om.provider

import scala.collection.Set
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

object ProvidersFactory {

  private var pepMatchProviders:HashMap[String, IPeptideMatchProvider]= new HashMap[String , IPeptideMatchProvider]()
  private var pepProviders:HashMap[String, IPeptideProvider]= new HashMap[String , IPeptideProvider]()
  private var ptmProviders:HashMap[String, IPTMProvider]= new HashMap[String , IPTMProvider]()
 
  //IPeptideMatchProvider methods
  def registerPeptideMatchProvider(keyProvider:String, matchProvider:IPeptideMatchProvider) =  {
    pepMatchProviders += keyProvider -> matchProvider
  }
  
  def unregisterPeptideMatchProvider(keyProvider:String) =  {
    pepMatchProviders.remove(keyProvider)
  }
  
  def getPeptideMatchProvider(keyProvider:String) : Option[IPeptideMatchProvider] = {
    pepMatchProviders.get(keyProvider) 
  }
  
  def getAvailablePeptideMatchProvider() : Set[String]  = {
    return pepMatchProviders.keySet
  }
  
  //IPeptideProvider methods
  def registerPeptideProvider(keyProvider:String, pepProvider:IPeptideProvider) =  {
    pepProviders += keyProvider -> pepProvider
  }
  
  def unregisterPeptideProvider(keyProvider:String) =  {
    pepProviders.remove(keyProvider)
  }
  
  def getPeptideProvider(keyProvider:String) : Option[IPeptideProvider] = {
    pepProviders.get(keyProvider) 
  }
  
  def getAvailablePeptideProvider() : Set[String]  = {
    return pepProviders.keySet
  }
  
    //IPTMProvider methods
  def registerPTMProvider(keyProvider:String, ptmProvider:IPTMProvider) =  {
    ptmProviders += keyProvider -> ptmProvider
  }
  
  def unregisterPTMProvider(keyProvider:String) =  {
    ptmProviders.remove(keyProvider)
  }
  
  def getPTMProvider(keyProvider:String) : Option[IPTMProvider] = {
    ptmProviders.get(keyProvider) 
  }
  
  def getAvailablePTMProvider() : Set[String]  = {
    return ptmProviders.keySet
  } 
}