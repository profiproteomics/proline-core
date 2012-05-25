package fr.proline.core.orm.utils;

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
	};
}
