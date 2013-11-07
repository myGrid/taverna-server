package org.taverna.server.master.scape;

import static org.taverna.server.master.rest.scape.Namespaces.T2FLOW_NS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.NoCreateException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;

public class SplicingEngine {
	@Value("${scape.wrapper}")
	private String wrapper;
	private String wrapperContents;

	@NonNull
	private Element getChild(@NonNull Element context, String... nodes) {
		Element e = context;
		for (String name : nodes) {
			e = (Element) e.getElementsByTagNameNS(T2FLOW_NS, name).item(0);
			if (e == null)
				throw new RuntimeException("no such element: " + name);
		}
		return e;
	}

	private List<Element> children(Element context, String name) {
		NodeList nl = context.getElementsByTagNameNS(T2FLOW_NS, name);
		List<Element> elems = new ArrayList<Element>(nl.getLength());
		for (int i = 0; i < nl.getLength(); i++)
			elems.add((Element) nl.item(i));
		return elems;
	}

	@NonNull
	Element getWrapperInstance() throws IOException,
			ParserConfigurationException, SAXException {
		if (wrapperContents == null)
			wrapperContents = IOUtils.toString(getClass().getResource(wrapper));
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		return db.parse(wrapperContents).getDocumentElement();
	}

	public Workflow constructWorkflow(@NonNull Element executablePlan,
			boolean haveSchematron) throws IOException,
			ParserConfigurationException, SAXException, NoCreateException {
		Element wrap = getWrapperInstance();

		// Splice in dataflows
		Element outerMaster = getOuterMaster(wrap);
		Element innerMaster = getInnerMasterAndSplice(executablePlan, wrap);

		// Splice in processor
		spliceSubworkflowProcessor(outerMaster, innerMaster);

		// FIXME Splice inputs and outputs

		// Splice into POJO
		Workflow w = new Workflow();
		w.content = new Element[1];
		w.content[0] = wrap;
		return w;
	}

	Element getInnerMasterAndSplice(Element executablePlan, Element wrap)
			throws NoCreateException {
		Element innerMaster = null;
		for (Element df : children(executablePlan, "dataflow")) {
			if (df.getAttribute("role").equals("top")) {
				innerMaster = df;
				df.setAttribute("role", "nested");
			}
			wrap.appendChild(df);
		}
		if (innerMaster == null)
			throw new NoCreateException(
					"executable plan workflow had no toplevel dataflow");
		return innerMaster;
	}

	Element getOuterMaster(Element wrap) throws NoCreateException {
		for (Element df : children(wrap, "dataflow"))
			if (df.getAttribute("role").equals("top"))
				return df;
		throw new NoCreateException(
				"template workflow had no toplevel dataflow");
	}

	void spliceSubworkflowProcessor(Element outer, Element inner) {
		for (Element processor : children(getChild(outer, "processors"),
				"processor")) {
			Element activity = getChild(processor, "activities", "activity");
			Element type = getChild(activity, "raven", "artifact");
			if (type.getTextContent().equals("dataflow-activity")) {
				Element dfConfig = getChild(activity, "configBean", "dataflow");
				dfConfig.setAttribute("ref", inner.getAttribute("id"));
				break;
			}
		}
	}
}
