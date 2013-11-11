package org.taverna.server.master.scape;

import static javax.xml.xpath.XPathConstants.BOOLEAN;
import static javax.xml.xpath.XPathConstants.NODE;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.NUMBER;
import static javax.xml.xpath.XPathConstants.STRING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.cxf.helpers.MapNamespaceContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class XPathSupport {
	XPathSupport(String... map) {
		Map<String, String> nsmap = new HashMap<String, String>();
		for (int i = 0; i < map.length; i += 2)
			nsmap.put(map[i], map[i + 1]);
		context = new MapNamespaceContext(nsmap);
	}

	final NamespaceContext context;

	private ThreadLocal<XPath> tlxp = new ThreadLocal<XPath>() {
		@Override
		protected XPath initialValue() {
			return XPathFactory.newInstance().newXPath();
		}
	};

	private XPath xp() {
		XPath xp = tlxp.get();
		xp.setNamespaceContext(context);
		return xp;
	}

	public List<Element> select(Element context, String expression)
			throws XPathExpressionException {
		List<Element> result = new ArrayList<Element>();
		NodeList nl = (NodeList) xp().evaluate(expression, context, NODESET);
		tlxp.get().reset();
		for (int i = 0; i < nl.getLength(); i++)
			result.add((Element) nl.item(i));
		return result;
	}

	public Element get(Element context, String expression)
			throws XPathExpressionException {
		try {
			return (Element) xp().evaluate(expression, context, NODE);
		} finally {
			tlxp.get().reset();
		}
	}

	public String text(Element context, String expression)
			throws XPathExpressionException {
		try {
			return (String) xp().evaluate(expression, context, STRING);
		} finally {
			tlxp.get().reset();
		}
	}

	public boolean isMatched(Element context, String expression)
			throws XPathExpressionException {
		try {
			return (Boolean) xp().evaluate(expression, context, BOOLEAN);
		} finally {
			tlxp.get().reset();
		}
	}

	public double number(Element context, String expression)
			throws XPathExpressionException {
		try {
			return (Double) xp().evaluate(expression, context, NUMBER);
		} finally {
			tlxp.get().reset();
		}
	}
}
