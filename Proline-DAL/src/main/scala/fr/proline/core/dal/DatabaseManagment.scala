package fr.proline.core.dal

import fr.proline.core.orm.utils.JPAUtil
import fr.proline.repository.DatabaseConnector
import fr.proline.repository.ProlineRepository
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence
import javax.persistence.TypedQuery
import fr.proline.core.orm.uds.ExternalDb
import scala.collection.mutable.Map
import fr.proline.repository.ConnectionPrototype
import scala.collection.JavaConversions
import fr.proline.core.orm.uds.Project
import javax.persistence.NoResultException

class DatabaseManagment (udsDBConnector : DatabaseConnector ){

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
		
		var propBuilder = Map.newBuilder[String, String]
		propBuilder += DatabaseConnector.PROPERTY_USERNAME ->pdiDB.getDbUser
		propBuilder += DatabaseConnector.PROPERTY_PASSWORD->pdiDB.getDbPassword
		propBuilder += DatabaseConnector.PROPERTY_DRIVERCLASSNAME ->udsDBConnector.getDriverType.getDriverClassName()
		propBuilder += DatabaseConnector.PROPERTY_URL ->createURL(pdiDB)
		
		 
		val pdiConn = new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(propBuilder.result))
		pdiConn
	}
	
	def getMSIDatabaseConnector(projectID : Int) : DatabaseConnector = {
	  var msiDB: ExternalDb = null
	  try {
		val udsEM = udsEMF.createEntityManager()
		val query : TypedQuery[Project] = udsEM.createQuery("Select prj from Project prj where prj.id =  :id", classOf[Project])
		query.setParameter("id", "projectID")
		val project = query.getSingleResult
		val assocMSI = JavaConversions.asScalaSet(project.getExternalDatabases).filter(p => {p.getType.equals("msi")})
		if(assocMSI.size>1)
		  throw new javax.persistence.NonUniqueResultException("Multiple MSI databases associated to this project")
		msiDB = assocMSI.iterator.next
	  } catch {
	    case nre:NoResultException  =>  return null
	  }
	  
		var propBuilder = Map.newBuilder[String, String]
		propBuilder += DatabaseConnector.PROPERTY_USERNAME ->msiDB.getDbUser
		propBuilder += DatabaseConnector.PROPERTY_PASSWORD->msiDB.getDbPassword
		propBuilder += DatabaseConnector.PROPERTY_DRIVERCLASSNAME ->udsDBConnector.getDriverType.getDriverClassName()
		propBuilder += DatabaseConnector.PROPERTY_URL ->createURL(msiDB)
		
		 
		val msiConn = new DatabaseConnector(JavaConversions.mutableMapAsJavaMap(propBuilder.result))
		msiConn
	}
	
	private def createURL(externalDB: ExternalDb) : String = {
		val URLbuilder : StringBuilder = new StringBuilder()
		val protocol  = udsDBConnector.getDriverType()
		URLbuilder.append("jdbc:").append(udsDBConnector.getDriverType().name().toLowerCase()).append(':').append(externalDB.getHost)
		if(externalDB.getPort != null)
			URLbuilder.append(":").append(externalDB.getPort)
		if(!URLbuilder.endsWith(":"))
		  URLbuilder.append("/")
		URLbuilder.append(externalDB.getDbName)
		URLbuilder.toString		
	}
}