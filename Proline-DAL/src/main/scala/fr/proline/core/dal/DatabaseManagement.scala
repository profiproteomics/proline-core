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
   
	private var externalDbIdToDBConnector : Map[Int, DatabaseConnector] = new HashMap[Int, DatabaseConnector] 
  
	private lazy val udsEMF : EntityManagerFactory  = {
		//Create Link to UDSDb
		val emf : EntityManagerFactory = Persistence.createEntityManagerFactory(JPAUtil.PersistenceUnitNames.getPersistenceUnitNameForDB(ProlineRepository.Databases.UDS), udsDBConnector.getEntityManagerSettings)
		emf
	}
	
	lazy val pdiDBConnector : DatabaseConnector = {
		val udsEM = udsEMF.createEntityManager()
		val query : TypedQuery[ExternalDb] = udsEM.createQuery("Select exDB from ExternalDb exDB where exDB.type = :type", classOf[ExternalDb])
		query.setParameter("type", "pdi")
		val pdiDB = query.getSingleResult
		udsEM.close		
		var propBuilder = Map.newBuilder[String, String]
		propBuilder += DatabaseConnector.PROPERTY_USERNAME ->pdiDB.getDbUser
		propBuilder += DatabaseConnector.PROPERTY_PASSWORD->pdiDB.getDbPassword
		propBuilder += DatabaseConnector.PROPERTY_DRIVERCLASSNAME ->udsDBConnector.getDriverType.getDriverClassName()
		propBuilder += DatabaseConnector.PROPERTY_URL ->createURL(pdiDB)
		
		 
		val pdiConn = new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(propBuilder.result))
		pdiConn
		
	}
  
	lazy val psDBConnector : DatabaseConnector = {
		val udsEM = udsEMF.createEntityManager()
		val query : TypedQuery[ExternalDb] = udsEM.createQuery("Select exDB from ExternalDb exDB where exDB.type = :type", classOf[ExternalDb])
		query.setParameter("type", "ps")
		val pdiDB = query.getSingleResult
		udsEM.close
		
		var propBuilder = Map.newBuilder[String, String]
		propBuilder += DatabaseConnector.PROPERTY_USERNAME ->pdiDB.getDbUser
		propBuilder += DatabaseConnector.PROPERTY_PASSWORD->pdiDB.getDbPassword
		propBuilder += DatabaseConnector.PROPERTY_DRIVERCLASSNAME ->udsDBConnector.getDriverType.getDriverClassName()
		propBuilder += DatabaseConnector.PROPERTY_URL ->createURL(pdiDB)
		
		 
		val pdiConn = new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(propBuilder.result))
		pdiConn
	}
	
	def getMSIDatabaseConnector(projectID : Int, createNew : Boolean = false) : DatabaseConnector = {	  
	  var msiDB: ExternalDb = null
	  try {
		val udsEM = udsEMF.createEntityManager()
		val query : TypedQuery[Project] = udsEM.createQuery("Select prj from Project prj where prj.id =  :id", classOf[Project])
		query.setParameter("id", projectID)		
		val project = query.getSingleResult
		val assocMSI = JavaConversions.asScalaSet(project.getExternalDatabases).filter(p => {p.getType.equals("msi")})
		udsEM.close
		if(assocMSI.size>1)
		  throw new javax.persistence.NonUniqueResultException("Multiple MSI databases associated to this project")
		msiDB = assocMSI.iterator.next
	  } catch {
	    case nre:NoResultException  =>  {
	      logger.warn("NoResultException  "+ nre.getMessage)
	      return null 
	    }
	  }
	  
	  if(createNew)
	    externalDbIdToDBConnector -= msiDB.getId
	    
	  if(!externalDbIdToDBConnector.contains(msiDB.getId)) {
	  
		var propBuilder = Map.newBuilder[String, String]
		propBuilder += DatabaseConnector.PROPERTY_USERNAME ->msiDB.getDbUser
		propBuilder += DatabaseConnector.PROPERTY_PASSWORD->msiDB.getDbPassword
		propBuilder += DatabaseConnector.PROPERTY_DRIVERCLASSNAME ->udsDBConnector.getDriverType.getDriverClassName()
		propBuilder += DatabaseConnector.PROPERTY_URL ->createURL(msiDB)
				 
		externalDbIdToDBConnector.put(msiDB.getId,new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(propBuilder.result)))
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
		      case DriverType.SQLITE => URLbuilder.append("memory:")
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