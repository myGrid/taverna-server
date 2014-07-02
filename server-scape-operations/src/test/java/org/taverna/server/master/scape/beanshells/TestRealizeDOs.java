package org.taverna.server.master.scape.beanshells;

import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TestRealizeDOs {

	@Test
	public void testResolution() throws Exception {
		RealizeDOs op = new RealizeDOs();
		URL rURL = new URL("file:/foo/");
		op.init("repository", rURL.toString());
		op.entBase = new URL(rURL, "entity/");
		op.repBase = new URL(rURL, "representation/");
		op.fileBase = new URL(rURL, "file/");
		List<String>in = Arrays.asList("a/b/c".split("/"));
		assertEquals("file:/foo/file/a/b/c", op.resolveContent(in).toString());
		assertEquals("file:/foo/representation/a/b", op.resolveRepresentation(in).toString());
		assertEquals("file:/foo/entity/a", op.resolveEntity(in).toString());
	}

}
