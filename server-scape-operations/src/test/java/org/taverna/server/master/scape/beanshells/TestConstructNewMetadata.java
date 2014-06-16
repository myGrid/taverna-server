package org.taverna.server.master.scape.beanshells;

import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.*;

import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

public class TestConstructNewMetadata {
	String originalMetadata;
	String newInformation;

	@Before
	public void loadData() throws Exception {
		originalMetadata = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_original.xml").toURI())));
		newInformation = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_newinfo.xml").toURI())));
	}

	@Test
	public void test() throws Exception {
		ConstructNewMetadata op = new ConstructNewMetadata()
				.init("contentType", "text/plain")
				.init("creator", getClass().toString())
				.init("generatedFilename", "/tmp/example/BCD.txt")
				.init("originalMetadata", originalMetadata)
				.init("newInformation", newInformation);
		op.perform();
		System.out.println(op.getResult("newMetadata"));
		assertNotEquals("", op.getResult("newMetadata"));
	}

}
