package fr.proline.core.dal

import fr.proline.core.orm.utils.JPAUtil
import fr.proline.repository.DatabaseConnector
import fr.proline.repository.ProlineRepository
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence
import javax.persistence.TypedQuery
import fr.proline.core.orm.uds.ExternalDb
import scala.collection.mutable.Map
import scala.collection.JavaConversions
import fr.proline.core.orm.uds.Project
import javax.persistence.NoResultException
import fr.proline.repository.ProlineRepository.DriverType
import com.weiglewilczek.slf4s.Logging
import scala.collection.mutable.HashMap

class DatabaseManagement (val udsDBConnector : DatabaseConnector ) extends Logging {
   
	private val udsDriverClassName = udsDBConnector.getDriverType.getDriverClassName()
	private val externalDbIdToDBConnector : Map[Int, DatabaseConnector] = new HashMap[Int, DatabaseConnector] 
  
	lazy val udsEMF : EntityManagerFactory  = {	
			Persistence.createEntityManagerFactory(
											JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.UDS),
											udsDBConnector.getEntityManagerSettings
		                                 )
	}
	
	private def externalDbToDbConnector( extDb: ExternalDb ): DatabaseConnector = {
	  
	  // TODO: retrieve driver class name from serialized properties
	  val driverClassName = this.udsDriverClassName
	  
      val properties = new HashMap[String, String]
      properties += DatabaseConnector.PROPERTY_USERNAME -> Option(extDb.getDbUser).getOrElse("")
      properties += DatabaseConnector.PROPERTY_PASSWORD -> Option(extDb.getDbPassword).getOrElse("")
      properties += DatabaseConnector.PROPERTY_DRIVERCLASSNAME -> driverClassName
      properties += DatabaseConnector.PROPERTY_URL -> createURL(extDb)
      
      // TODO: retrieve the dialect from serialized properties
      if( driverClassName == DriverType.valueOf("POSTGRESQL").getDriverClassName() )
        properties += DatabaseConnector.PROPERTY_DIALECT -> "fr.proline.core.orm.utils.TableNameSequencePostgresDialect"
      
      new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(properties))
	}
	
	lazy val pdiDBConnector : DatabaseConnector = {
		val udsEM = udsEMF.createEntityManager()	
		val query : TypedQuery[ExternalDb] = udsEM.createQuery("Select exDB from ExternalDb exDB where exDB.type = :type", classOf[ExternalDb])
		query.setParameter("type", "pdi")
		val pdiDB = query.getSingleResult
		udsEM.close
		
		externalDbToDbConnector(pdiDB)
		
	}
	
    lazy val pdiEMF : EntityManagerFactory  = {
			Persistence.createEntityManagerFactory(
											JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.PDI),
											pdiDBConnector.getEntityManagerSettings
										)								
	}
  
	lazy val psDBConnector : DatabaseConnector = {
		val udsEM = udsEMF.createEntityManager()
		val query : TypedQuery[ExternalDb] = udsEM.createQuery("Select exDB from ExternalDb exDB where exDB.type = :type", classOf[ExternalDb])
		query.setParameter("type", "ps")
		val psDB = query.getSingleResult
		udsEM.close
		
		externalDbToDbConnector(psDB)
	}

	lazy val psEMF : EntityManagerFactory = {
	  Persistence.createEntityManagerFactory(
	      JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.PS),
	      psDBConnector.getEntityManagerSettings
	      )
	}
	
	def getMSIDatabaseConnector(projectID : Int, createNew : Boolean = false) : DatabaseConnector = {	  
	  var msiDB: ExternalDb = null
	  try {
  		val udsEM = udsEMF.createEntityManager()
  		val query : TypedQuery[Project] = udsEM.createQuery("Select prj from Project prj where prj.id =  :id", classOf[Project])
  		query.setParameter("id", projectID)		
  		val project = query.getSingleResult
  		val assocMSIdbs = JavaConversions.asScalaSet(project.getExternalDatabases).filter(_.getType == "msi").toList
  		udsEM.close
  		
  		if(assocMSIdbs.size>1) {
  		  throw new javax.persistence.NonUniqueResultException("Multiple MSI databases associated to this project")
  		} else if( assocMSIdbs.size == 0 ) {
        throw new Exception("no MSIdb is linked to this project")
  		}
  		
		  msiDB = assocMSIdbs(0)
		
	  } catch {
	    case nre:NoResultException  =>  {
	      logger.warn("NoResultException  "+ nre.getMessage)
	      return null 
	    }
	  }
	  
	  if(createNew)
	    externalDbIdToDBConnector -= msiDB.getId
	    
	  if( !externalDbIdToDBConnector.contains(msiDB.getId) ) {	  
  		externalDbIdToDBConnector.put(msiDB.getId,externalDbToDbConnector(msiDB) )
	  }
	  
	  externalDbIdToDBConnector(msiDB.getId)
	  
	}
	
	private def createURL(externalDB: ExternalDb) : String = {
		val URLbuilder : StringBuilder = new StringBuilder()
		val protocol  = udsDBConnector.getDriverType()
		URLbuilder.append("jdbc:").append(udsDBConnector.getDriverType().name().toLowerCase()).append(':')
		externalDB.getConnectionMode match {
		  case "HOST" => {
		    URLbuilder.append("//").append(externalDB.getHost)
		    if(externalDB.getPort != null)
		    	URLbuilder.append(":").append(externalDB.getPort)
		    URLbuilder.append('/').append(externalDB.getDbName)
		  } 
		  
		  case "MEMORY" => {
		    udsDBConnector.getDriverType match {
		      case DriverType.SQLITE => URLbuilder.append(":memory:")
		      case _ =>  URLbuilder.append("mem:").append(externalDB.getDbName)		        		      			      
		    }
		  }
		  
		  case "FILE" => {
		    udsDBConnector.getDriverType match {
		      case DriverType.H2 => URLbuilder.append("file:").append(externalDB.getDbName)
		      case _ => URLbuilder.append(externalDB.getDbName)		      
		    }
		  }		 
		}
		URLbuilder.toString		
	}
	
	def closeAll(){
	  udsEMF.close
	  udsDBConnector.closeAll
	}
}