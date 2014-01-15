package org.taverna.server.master.scape;

import static java.lang.String.format;
import static javax.xml.transform.OutputKeys.INDENT;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static javax.xml.xpath.XPathConstants.BOOLEAN;
import static javax.xml.xpath.XPathConstants.NODE;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.NUMBER;
import static javax.xml.xpath.XPathConstants.STRING;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.cxf.helpers.MapNamespaceContext;
import org.springframework.core.NamedThreadLocal;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

class XPathSupport {
	XPathSupport(Log log, String... map) {
		Map<String, String> nsmap = new HashMap<String, String>();
		for (int i = 0; i < map.length; i += 2)
			nsmap.put(map[i], map[i + 1]);
		context = new MapNamespaceContext(nsmap);
		this.log = log;
	}

	private final Log log;
	private final NamespaceContext context;

	@SuppressWarnings("serial")
	private class XPathMap extends HashMap<String, XPathExpression> {
		final XPath factory;

		XPathMap() {
			factory = XPathFactory.newInstance().newXPath();
			factory.setNamespaceContext(context);
		}

		XPathExpression compile(String expression)
				throws XPathExpressionException {
			if (!containsKey(expression)) {
				log.info("compiling expression for " + expression);
				put(expression, factory.compile(expression));
			}
			return get(expression);
		}
	}

	private ThreadLocal<XPathMap> cache = new NamedThreadLocal<XPathMap>(
			"XPathCache") {
		@Override
		protected XPathMap initialValue() {
			return new XPathMap();
		}
	};

	private XPathExpression xp(String expression, Object[] args)
			throws XPathExpressionException {
		return cache.get().compile(format(expression, args));
	}

	public List<Element> select(Element context, String expression,
			Object... args) throws XPathExpressionException {
		List<Element> result = new ArrayList<Element>();
		NodeList nl = (NodeList) xp(expression, args)
				.evaluate(context, NODESET);
		for (int i = 0; i < nl.getLength(); i++)
			result.add((Element) nl.item(i));
		return result;
	}

	public List<Node> selectNodes(Element context, String expression,
			Object... args) throws XPathExpressionException {
		List<Node> result = new ArrayList<Node>();
		NodeList nl = (NodeList) xp(expression, args)
				.evaluate(context, NODESET);
		for (int i = 0; i < nl.getLength(); i++)
			result.add(nl.item(i));
		return result;
	}

	@NonNull
	public Element get(Element context, String expression, Object... args)
			throws XPathExpressionException {
		Element e = (Element) xp(expression, args).evaluate(context, NODE);
		if (e == null)
			throw new RuntimeException("nothing matched "
					+ format(expression, args));
		return e;
	}

	@Nullable
	public Element getMaybe(Element context, String expression, Object... args)
			throws XPathExpressionException {
		return (Element) xp(expression, args).evaluate(context, NODE);
	}

	@NonNull
	@SuppressWarnings("null")
	public String text(Element context, String expression, Object... args)
			throws XPathExpressionException {
		return (String) xp(expression, args).evaluate(context, STRING);
	}

	public boolean isMatched(Element context, String expression, Object... args)
			throws XPathExpressionException {
		return (Boolean) xp(expression, args).evaluate(context, BOOLEAN);
	}

	public double number(Element context, String expression, Object... args)
			throws XPathExpressionException {
		return (Double) xp(expression, args).evaluate(context, NUMBER);
	}

	public Element read(String doc) throws ParserConfigurationException,
			SAXException, IOException {
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		return db.parse(new InputSource(new StringReader(doc)))
				.getDocumentElement();
	}

	public String write(Element elem) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance()
				.newTransformer();
		transformer.setOutputProperty(INDENT, "yes");
		transformer.setOutputProperty(OMIT_XML_DECLARATION, "yes");

		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(elem);
		transformer.transform(source, result);

		return result.getWriter().toString();
	}
}
