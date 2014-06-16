package org.taverna.server.master.scape.beanshells;

import static java.lang.Boolean.FALSE;
import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class TestAssessMetrics {
	String metrics;
	String schema;

	@Before
	public void setUp() throws Exception {
		metrics = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_newinfo.xml").toURI())));
		schema = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_schematron.xml").toURI())));
	}

	@Test
	public void test() throws Exception {
		AssessMetrics op = new AssessMetrics();
		op.init("QLD", schema);
		op.init("metrics", metrics);
		op.perform();
		assertEquals(FALSE, op.getResult("isSatisfied"));
		assertEquals(Arrays.asList("pixel count acceptable", "compression ratio acceptable"), op.getResult("failures"));
	}

}
