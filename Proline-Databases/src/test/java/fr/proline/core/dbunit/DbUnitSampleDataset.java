package fr.proline.core.dbunit;

public enum DbUnitSampleDataset {
	
    MSI_SEARCH("/dbunit/datasets/msi/MsiSearch_Dataset.xml"),
    RESULT_SET("/dbunit/datasets/msi/Resultset_Dataset.xml"),
    PEPTIDES("/dbunit/datasets/msi/Peptides_Dataset.xml"),
    PROJECT("/dbunit/datasets/uds/Project_Dataset.xml"),
    QUANTI_15N("/dbunit/datasets/uds/Quanti_15N_Dataset.xml");
    
	private final String _resourcePath;

	DbUnitSampleDataset(String resourcePath) {
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