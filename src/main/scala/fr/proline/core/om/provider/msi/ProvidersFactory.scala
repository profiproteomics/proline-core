package fr.proline.core.om.provider.msi

import scala.collection.Set
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

object ProvidersFactory {

  final val DEFAULT_KEY_PROVIDERS: String = "DEFAULT"
    
  private var pepMatchProviders:HashMap[String, IPeptideMatchProvider]= new HashMap[String , IPeptideMatchProvider]()
  private var pepProviders:HashMap[String, IPeptideProvider]= new HashMap[String , IPeptideProvider]()
  private var ptmProviders:HashMap[String, IPTMProvider]= new HashMap[String , IPTMProvider]()
  private var protProviders:HashMap[String, IProteinProvider]= new HashMap[String , IProteinProvider]()
 
  //IPeptideMatchProvider methods
  def registerPeptideMatchProvider(keyProvider:String, matchProvider:IPeptideMatchProvider) =  {
    pepMatchProviders += keyProvider -> matchProvider
  }
  
  def unregisterPeptideMatchProvider(keyProvider:String) =  {
    pepMatchProviders.remove(keyProvider)
  }
  
  def getPeptideMatchProvider(keyProvider:String, retDefault:Boolean) : Option[IPeptideMatchProvider] = {
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
  
    
    //IProteinProvider methods
  def registerProteinProvider(keyProvider:String, protProvider:IProteinProvider) =  {
    protProviders += keyProvider -> protProvider
  }
  
  def unregisterProteinProvider(keyProvider:String) =  {
    protProviders.remove(keyProvider)
  }
  
  def getProteinProvider(keyProvider:String) : Option[IProteinProvider] = {
    protProviders.get(keyProvider) 
  }
  
  def getAvailableProteinProvider() : Set[String]  = {
    return protProviders.keySet
  } 
}