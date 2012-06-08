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
  private var seqDbProviders:HashMap[String, ISeqDatabaseProvider]= new HashMap[String , ISeqDatabaseProvider]()  
 
  //IPeptideMatchProvider methods
  def registerPeptideMatchProvider(keyProvider:String, matchProvider:IPeptideMatchProvider) =  {
    pepMatchProviders += keyProvider -> matchProvider
  }
  
  def unregisterPeptideMatchProvider(keyProvider:String) =  {
    pepMatchProviders.remove(keyProvider)
  }
  
  def getPeptideMatchProvider(keyProvider:String, retDefault:Boolean) : Option[IPeptideMatchProvider] = {
    if(pepMatchProviders.get(keyProvider) == None && retDefault)
      pepMatchProviders.get(DEFAULT_KEY_PROVIDERS)
    else 
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
  
  def getPeptideProvider(keyProvider:String, retDefault:Boolean) : Option[IPeptideProvider] = {
     if(pepProviders.get(keyProvider) == None && retDefault)
      pepProviders.get(DEFAULT_KEY_PROVIDERS)
    else 
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
  
  def getPTMProvider(keyProvider:String, retDefault:Boolean) : Option[IPTMProvider] = {
    if(ptmProviders.get(keyProvider) == None && retDefault)
      ptmProviders.get(DEFAULT_KEY_PROVIDERS)
    else 
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
  
  def getProteinProvider(keyProvider:String, retDefault:Boolean) : Option[IProteinProvider] = {
    if(protProviders.get(keyProvider) == None && retDefault)
      protProviders.get(DEFAULT_KEY_PROVIDERS)
    else 
      protProviders.get(keyProvider) 
  }
  
  def getAvailableProteinProvider() : Set[String]  = {
    return protProviders.keySet
  } 
  
  
    //ISeqDatabaseProviders methods
  def registerSeqDatabaseProvider(keyProvider:String, seqDbProvider:ISeqDatabaseProvider) =  {
    seqDbProviders += keyProvider -> seqDbProvider
  }
  
  def unregisterSeqDatabaseProvider(keyProvider:String) =  {
    seqDbProviders.remove(keyProvider)
  }
  
  def getSeqDatabaseProvider(keyProvider:String, retDefault:Boolean) : Option[ISeqDatabaseProvider] = {
    if(seqDbProviders.get(keyProvider) == None && retDefault)
      seqDbProviders.get(DEFAULT_KEY_PROVIDERS)
    else 
      seqDbProviders.get(keyProvider) 
  }
}