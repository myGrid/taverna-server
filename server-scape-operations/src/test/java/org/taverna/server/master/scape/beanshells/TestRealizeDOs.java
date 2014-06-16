package org.taverna.server.master.scape.beanshells;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestRealizeDOs {

	@Test
	public void testResolution() throws Exception {
		RealizeDOs op = new RealizeDOs();
		op.init("repository", "file:/foo");
		assertEquals("file:/foo/a/b/c", op.resolveContent("a/b/c").toString());
		assertEquals("file:/foo/a/b", op.resolveRepresentation("a/b/c").toString());
	}

}
