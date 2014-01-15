package org.taverna.server.master.scape;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

abstract class DOMUtils {
	private DOMUtils() {
	}

	static Element attrs(Element element, String... definitions) {
		if ((definitions.length & 1) == 1)
			throw new IllegalArgumentException(
					"need an even number of definition strings");
		for (int i = 0; i < definitions.length; i += 2)
			element.setAttribute(definitions[i], definitions[i + 1]);
		return element;
	}

	@NonNull
	static Element branch(@NonNull Element parent, String... name) {
		if (name.length == 0)
			throw new IllegalArgumentException("need at least one element name");
		Document doc = parent.getOwnerDocument();
		Element elem = parent;
		for (String n : name) {
			@NonNull
			@SuppressWarnings("null")
			Element created = doc.createElementNS(elem.getNamespaceURI(), n);
			elem.appendChild(created);
			elem = created;
		}
		return elem;
	}

	static Element leaf(Element parent, String name, String value) {
		Element elem = parent.getOwnerDocument().createElementNS(
				parent.getNamespaceURI(), name);
		elem.setTextContent(value);
		parent.appendChild(elem);
		return elem;
	}

	static void cls(@NonNull Element parent, String group, String artifact,
			String version, String clazz) {
		Element raven = branch(parent, "raven");
		leaf(raven, "group", group);
		leaf(raven, "artifact", artifact);
		leaf(raven, "version", version);
		leaf(parent, "class", clazz);
	}

	static Element config(@NonNull Element parent, String name) {
		Element config = attrs(branch(parent, "configBean"), "encoding",
				"xstream");
		Element created = parent.getOwnerDocument().createElementNS("", name);
		created.setPrefix("");
		config.appendChild(created);
		return created;
	}

	static Element datalink(@NonNull Element datalinks, String fromProc,
			String fromPort, String toProc, String toPort) {
		Element link = branch(datalinks, "datalink");
		Element sink = attrs(branch(link, "sink"), "type",
				toProc == null ? "dataflow" : "processor");
		if (toProc != null)
			leaf(sink, "processor", toProc);
		leaf(sink, "port", toPort);
		Element source = attrs(branch(link, "source"), "type",
				fromProc == null ? "dataflow" : "processor");
		if (fromProc != null)
			leaf(source, "processor", fromProc);
		leaf(source, "port", fromPort);
		return link;
	}

	@NonNull
	static Element port(@NonNull Element container, @NonNull String name,
			@Nullable String depth, @Nullable String granularDepth) {
		Element port = branch(container, "port");
		leaf(port, "name", name);
		if (depth != null)
			leaf(port, "depth", depth);
		if (granularDepth != null)
			leaf(port, "granularDepth", granularDepth);
		return port;
	}

}
