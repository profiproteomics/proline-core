package fr.proline.core.dbunit;

public enum DbUnitInitDataset {
	
    LCMS_DB("/dbunit/datasets/lcms-db_init_dataset.xml"),
    MSI_DB("/dbunit/datasets/msi-db_init_dataset.xml"),
    PDI_DB("/dbunit/datasets/pdi-db_init_dataset.xml"),
    PS_DB("/dbunit/datasets/ps-db_init_dataset.xml"),
    UDS_DB("/dbunit/datasets/uds-db_init_dataset.xml");
    
	private final String _resourcePath;

	DbUnitInitDataset(String resourcePath) {
		this._resourcePath = resourcePath;
	}

	public String getResourcePath() {
		return _resourcePath;
	}

	@Override
	public String toString() {
		return _resourcePath;
	}

}