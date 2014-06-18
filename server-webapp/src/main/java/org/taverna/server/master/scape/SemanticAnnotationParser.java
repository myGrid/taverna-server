package org.taverna.server.master.scape;

import static org.apache.commons.logging.LogFactory.getLog;

import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.w3c.dom.Element;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

class SemanticAnnotationParser {
	private static final Log log = getLog("Taverna.Server.WorkflowSplicing.Scape");
	private static final Pattern NAME_EXTRACT = Pattern
			.compile("[a-zA-Z0-9]+$");
	Model annotationModel;
	Resource base;

	public SemanticAnnotationParser(String baseSubject, String turtle) {
		annotationModel = ModelFactory.createDefaultModel();
		annotationModel.read(new StringReader(turtle), baseSubject, "TTL");
		base = annotationModel.createResource(baseSubject);
		log.debug("parsed turtle \"" + turtle + "\" with base " + base);
	}

	public SemanticAnnotationParser(String baseSubject,
			List<Element> turtleContainers) {
		annotationModel = ModelFactory.createDefaultModel();
		base = annotationModel.createResource(baseSubject);
		for (Element e : turtleContainers) {
			String turtle = e.getTextContent();
			if (turtle.trim().isEmpty())
				continue;
			annotationModel.read(new StringReader(turtle), baseSubject, "TTL");
			log.debug("parsed turtle \"" + turtle + "\" with base " + base);
		}
	}

	public String getProperty(String propertyURI) {
		Property subjectProperty = annotationModel.getProperty(propertyURI);
		for (Statement s : annotationModel.listStatements(base,
				subjectProperty, (RDFNode) null).toList()) {
			RDFNode node = s.getObject();
			if (node == null)
				continue;
			if (node.isLiteral())
				return node.asLiteral().getValue().toString();
			if (node.isResource()) {
				String uri = node.asResource().getURI();
				if (uri != null)
					return uri;
			}
			log.info("got a node for " + propertyURI
					+ "that I can't interpret: " + node);
		}
		return null;
	}

	@SuppressWarnings("unused")
	private String strip(String name) {
		Matcher m = NAME_EXTRACT.matcher(name.trim());
		return m.find() ? m.group().trim() : name;
	}
}