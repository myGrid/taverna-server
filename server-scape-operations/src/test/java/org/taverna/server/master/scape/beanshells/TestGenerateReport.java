package org.taverna.server.master.scape.beanshells;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TestGenerateReport {
	@Test
	public void testWithNoErrorsUnassessedNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList(null, null));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 2 successful writes and 0 errors."
				+ "<h2>Written</h2><ul>"
				+ "<li>Object a would have been written back to 1.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "<li>Object b would have been written back to 2.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "</ul>"
				+ "<h2>Errors</h2><ul></ul>", op.getResult("report"));
	}

	@Test
	public void testWithNoErrorsUnassessedNoWriteString() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", "false");
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList(null, null));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 2 successful writes and 0 errors."
				+ "<h2>Written</h2><ul>"
				+ "<li>Object a would have been written back to 1.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "<li>Object b would have been written back to 2.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "</ul>"
				+ "<h2>Errors</h2><ul></ul>", op.getResult("report"));
	}

	@Test
	public void testWithNoErrorsNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList(null, null));
		op.init("assessErrors", asList(asList(emptyList()), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 2 successful writes and 0 errors."
				+ "<h2>Written</h2><ul>"
				+ "<li>Object a would have been written back to 1.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "<li>Object b would have been written back to 2.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "</ul>"
				+ "<h2>Errors</h2><ul></ul>", op.getResult("report"));
	}

	@Test
	public void testWithWriteErrorsUnassessedNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList("foo", "bar"));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 0 successful writes and 2 errors."
				+ "<h2>Written</h2><ul></ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object a failed in upload.<br>foo</ul></li>"
				+ "<li>Object b failed in upload.<br>bar</ul></li>"
				+ "</ul>", op.getResult("report"));
	}

	@Test
	public void testWithWriteErrorsNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList("foo", "bar"));
		op.init("assessErrors", asList(asList(emptyList()), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 0 successful writes and 2 errors."
				+ "<h2>Written</h2><ul></ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object a failed in upload.<br>foo</ul></li>"
				+ "<li>Object b failed in upload.<br>bar</ul></li>"
				+ "</ul>", op.getResult("report"));
	}

	@Test
	public void testWithAssessErrorsNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList("foo", "bar"));
		op.init("assessErrors", asList(asList(asList("sp", "qr")), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 0 successful writes and 2 errors."
				+ "<h2>Written</h2><ul></ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object a failed assessment.<ul><li>sp</li><li>qr</li></ul></li>"
				+ "<li>Object b failed in upload.<br>bar</ul></li>"
				+ "</ul>", op.getResult("report"));
	}

	@Test
	public void testWithNoErrorsWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", true);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList(null, null));
		op.init("assessErrors", asList(asList(emptyList()), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 2 successful writes and 0 errors."
				+ "<h2>Written</h2><ul>"
				+ "<li>Object a was written back to 1.txt in repository http://example.com/</li>"
				+ "<li>Object b was written back to 2.txt in repository http://example.com/</li>"
				+ "</ul>"
				+ "<h2>Errors</h2><ul></ul>", op.getResult("report"));
	}


	@Test
	public void testWithWriteErrorsWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", true);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList("foo", "bar"));
		op.init("assessErrors", asList(asList(emptyList()), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 0 successful writes and 2 errors."
				+ "<h2>Written</h2><ul></ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object a failed in upload.<br>foo</ul></li>"
				+ "<li>Object b failed in upload.<br>bar</ul></li>"
				+ "</ul>", op.getResult("report"));
	}

	@Test
	public void testWithAssessErrorsWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", true);
		op.init("objects", asList("a", "b"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/"));
		op.init("writeErrors", asList("foo", "bar"));
		op.init("assessErrors", asList(asList(asList("sp", "qr")), asList(emptyList())));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 0 successful writes and 2 errors."
				+ "<h2>Written</h2><ul></ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object a failed assessment.<ul><li>sp</li><li>qr</li></ul></li>"
				+ "<li>Object b failed in upload.<br>bar</ul></li>"
				+ "</ul>", op.getResult("report"));
	}

	@Test
	public void testMixedNoWrite() throws Exception {
		GenerateReport op = new GenerateReport();
		assertNull(op.getResult("report"));
		op.init("doWrite", false);
		op.init("objects", asList("a", "b", "c"));
		op.init("writtenInfo", asList("1.txt;http://example.com/", "2.txt;http://example.com/", "3.txt;http://example.com/"));
		op.init("writeErrors", asList(null, "kaboom", null));
		op.init("assessErrors", asList(asList(emptyList()), asList(emptyList()), asList(asList("foo bar and grill"))));
		op.perform();
		assertEquals("<h2>Summary</h2>There were 1 successful writes and 2 errors."
				+ "<h2>Written</h2><ul>"
				+ "<li>Object a would have been written back to 1.txt in repository http://example.com/ (write-back was inhibited by policy)</li>"
				+ "</ul>"
				+ "<h2>Errors</h2><ul>"
				+ "<li>Object b failed in upload.<br>kaboom</ul></li>"
				+ "<li>Object c failed assessment.<ul><li>foo bar and grill</li></ul></li>"
				+ "</ul>", op.getResult("report"));
	}

}
