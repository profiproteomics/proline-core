package fr.proline.core.orm.utils;

import fr.proline.repository.ProlineRepository;

public class JPAUtil {

	public enum PersistenceUnitNames{		
		
		UDS_Key("udsdb_production"), PS_Key("psdb_production"), MSI_Key("msidb_production"),PDI_Key("pdidb_production");
		String pu_name;
		PersistenceUnitNames(String name){
			pu_name = name;
		}
		
		public String getPersistenceUnitName(){
			return pu_name;
		}
		
		public static String getPersistenceUnitNameForDB(ProlineRepository.Databases db){
			switch(db) {
			case LCMS:
				return null;
			case  MSI:
				return MSI_Key.getPersistenceUnitName();
			case PDI:
				return PDI_Key.getPersistenceUnitName();
			case PS:
				return PS_Key.getPersistenceUnitName();
			case UDS:
				return UDS_Key.getPersistenceUnitName();
			default :
				return null;
			}
		}
	};
}
