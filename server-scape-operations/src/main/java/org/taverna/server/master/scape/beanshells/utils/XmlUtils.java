package org.taverna.server.master.scape.beanshells.utils;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlUtils {
	private static final DocumentBuilderFactory factory;
	private static final TransformerFactory tFactory;
	static {
		factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		tFactory = TransformerFactory.newInstance();
	}

	private XmlUtils() {
	}

	public static Document makeNewDocument() throws ParserConfigurationException {
		return factory.newDocumentBuilder().newDocument();
	}
	public static Document parseDocument(String documentText)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(documentText));
		return builder.parse(is);
	}

	public static String serializeDocument(Node document) throws IOException,
			TransformerFactoryConfigurationError, TransformerException {
		Transformer transformer = tFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		StreamResult result = new StreamResult(new StringWriter());
		transformer.transform(new DOMSource(document), result);
		return result.getWriter().toString();
	}
}
