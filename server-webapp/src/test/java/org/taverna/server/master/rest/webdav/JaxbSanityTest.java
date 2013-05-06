package org.taverna.server.master.rest.webdav;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

import org.junit.Before;
import org.junit.Test;

/**
 * This test file ensures that the JAXB bindings will work once deployed instead
 * of mysteriously failing in service.
 * 
 * @author Donal Fellows
 */
public class JaxbSanityTest {
	SchemaOutputResolver sink;
	StringWriter schema;

	String schema() {
		return schema.toString();
	}

	@Before
	public void init() {
		schema = new StringWriter();
		sink = new SchemaOutputResolver() {
			@Override
			public Result createOutput(String namespaceUri,
					String suggestedFileName) throws IOException {
				StreamResult sr = new StreamResult(schema);
				sr.setSystemId("/dev/null");
				return sr;
			}
		};
		assertEquals("", schema());
	}

	private boolean printSchema = false;

	private void testJAXB(Class<?>... classes) throws Exception {
		JAXBContext.newInstance(classes).generateSchema(sink);
		if (printSchema)
			System.out.println(schema());
		assertTrue(schema().length() > 0);
	}

	@Test
	public void testJAXBforWebDAVRequests() throws Exception {
		testJAXB(Elements.Requests.LockInfo.class,
				Elements.Requests.PropertyFind.class,
				Elements.Requests.PropertyUpdate.class, Elements.Property.class);
	}

	@Test
	public void testJAXBforWebDAVResponses() throws Exception {
		testJAXB(Elements.Responses.Response.class,
				Elements.Responses.Error.class,
				Elements.Responses.MultiStatus.class, Elements.Property.class);
	}

	@Test
	public void testJAXBforWebDAV() throws Exception {
		testJAXB(Elements.Requests.LockInfo.class,
				Elements.Requests.PropertyFind.class,
				Elements.Requests.PropertyUpdate.class,
				Elements.Responses.Response.class,
				Elements.Responses.Error.class,
				Elements.Responses.MultiStatus.class, Elements.Property.class);
	}
}
