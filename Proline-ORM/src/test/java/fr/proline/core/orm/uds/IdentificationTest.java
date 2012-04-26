package fr.proline.core.orm.uds;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.proline.core.orm.uds.repository.IdentificationRepository;
import fr.proline.core.orm.utils.DatabaseTestCase;

public class IdentificationTest extends DatabaseTestCase {

	IdentificationRepository identificationRepo;

	@Before public void setUp() throws Exception {
        initDatabase();
        initEntityManager("udsdb_production");
        loadDataSet("/fr/proline/core/orm/uds/Project_Dataset.xml");
        identificationRepo = new IdentificationRepository(em);
	}

	@After public void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test public void readIdentification() {
		Project project = em.find(Project.class, 1);		
		List<Identification> identifications = identificationRepo.findIdentificationsByProject(project.getId());
		assertThat(identifications.size(), is(1));
		Identification identification = identifications.get(0);
		assertThat(identification, notNullValue());
		assertThat(identification.getName(), equalTo("CB_342"));
		assertThat(identification.getNumber(), is(1));
		assertThat(identification.getFractions().size(), is(identification.getFractionCount()));
	}

	@Test public void getIdentificationNames() {
		Project project = em.find(Project.class, 1);		
		List<String> identifications = identificationRepo.findIdentificationNamesByProject(project.getId());
		assertThat(identifications.size(), is(1));
		assertThat(identifications.get(0), equalTo("CB_342"));
	}

}
