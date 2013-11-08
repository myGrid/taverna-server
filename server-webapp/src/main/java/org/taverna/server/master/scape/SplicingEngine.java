package org.taverna.server.master.scape;

import static org.taverna.server.master.rest.scape.Namespaces.T2FLOW_NS;
import static org.w3c.dom.Node.ELEMENT_NODE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.NoCreateException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;

public class SplicingEngine {
	/** The name of the processor to splice. Must be a dataflow processor! */
	public static final String SPLICE_PROCESSOR_NAME = "preservationActionPlan";
	@Value("${scape.wrapper}")
	private String wrapper;
	private String wrapperContents;

	public static enum Model {
		One2OneNoSchema("1to1"), One2OneSchema("1to1_schema");
		private final String key;

		Model(String key) {
			this.key = key;
		}

		public String getKey() {
			return key;
		}
	}

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

	@NonNull
	private List<Element> children(@NonNull Element context,
			@NonNull String name) {
		NodeList nl = context.getElementsByTagNameNS(T2FLOW_NS, name);
		List<Element> elems = new ArrayList<Element>(nl.getLength());
		for (int i = 0; i < nl.getLength(); i++)
			elems.add((Element) nl.item(i));
		return elems;
	}

	@NonNull
	public Element getWrapperInstance(@NonNull Model model) throws IOException,
			ParserConfigurationException, SAXException {
		// TODO use key from model to look up resource name
		model.getKey();
		if (wrapperContents == null)
			wrapperContents = IOUtils.toString(getClass().getResource(wrapper));
		DocumentBuilder db = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		return db.parse(wrapperContents).getDocumentElement();
	}

	@SuppressWarnings("unused")
	public Workflow constructWorkflow(@NonNull Element executablePlan,
			@NonNull Model model) throws IOException,
			ParserConfigurationException, SAXException, NoCreateException {
		Element wrap = getWrapperInstance(model);

		// Splice in dataflows
		Element outerMaster = getOuterMaster(wrap);
		Element innerMaster = getInnerMasterAndSplice(executablePlan, wrap);

		// Splice in processor
		Element dataflowProcessor = spliceSubworkflowProcessor(outerMaster,
				innerMaster);
		Set<String> createdIn = spliceInputs(dataflowProcessor, outerMaster,
				innerMaster);
		Set<String> createdOut = spliceOutputs(dataflowProcessor, outerMaster,
				innerMaster);
		// TODO wiring up "unknown" inputs
		// TODO wiring up "unknown" outputs

		// Splice into POJO
		Workflow w = new Workflow();
		w.content = new Element[1];
		w.content[0] = wrap;
		return w;
	}

	/**
	 * Connects the spliced dataflow to
	 * 
	 * @param processor
	 * @param outer
	 * @param inner
	 * @return
	 */
	@NonNull
	Set<String> spliceInputs(@NonNull Element processor,
			@NonNull Element outer, @NonNull Element inner) {
		Document doc = outer.getOwnerDocument();
		Set<String> created = new HashSet<String>();

		// Construct process ports for inputs on the inner dataflow
		Element procInputs = getChild(processor, "inputPorts");
		Map<String, Integer> procInMap = new HashMap<String, Integer>();
		for (Element pin : children(procInputs, "port"))
			procInMap.put(getChild(pin, "name").getTextContent(), new Integer(
					getChild(pin, "depth").getTextContent()));
		Element innerInputs = getChild(inner, "inputPorts");
		Map<String, Integer> added = new HashMap<String, Integer>();
		for (Element in : children(innerInputs, "port")) {
			String name = getChild(in, "name").getTextContent();
			if (procInMap.containsKey(name))
				continue;
			Element newPort = doc.createElementNS(T2FLOW_NS, "port");
			Element newName, newDepth;
			newPort.appendChild(newName = doc
					.createElementNS(T2FLOW_NS, "name"));
			newName.setTextContent(name);
			newPort.appendChild(newDepth = doc.createElementNS(T2FLOW_NS,
					"depth"));
			String d = getChild(in, "depth").getTextContent();
			newDepth.setTextContent(d);
			procInputs.appendChild(newPort);
			added.put(name, new Integer(d));
		}
		procInMap.putAll(added);

		// Construct mapping between inside and outside
		Element inputMapElement = getChild(processor, "activities", "activity",
				"inputMap");
		Map<String, Element> inputMapping = new HashMap<String, Element>();
		for (Element e : children(inputMapElement, "map"))
			inputMapping.put(e.getAttribute("from"), e);
		for (String name : procInMap.keySet()) {
			if (inputMapping.containsKey(name))
				continue;
			Element newMap = doc.createElementNS(T2FLOW_NS, "map");
			newMap.setAttribute("from", name);
			newMap.setAttribute("to", name);
			inputMapElement.appendChild(newMap);
		}

		// Construct iteration strategy
		Element iterationStrategy = getChild(processor,
				"iterationStrategyStack", "iteration", "strategy");
		NodeList nl = iterationStrategy.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++)
			if (nl.item(i).getNodeType() == ELEMENT_NODE)
				iterationStrategy.removeChild(nl.item(i));
		// Categorise ports by their depth
		List<Element> dots = new ArrayList<Element>();
		List<Element> crosses = new ArrayList<Element>();
		for (Entry<String, Integer> item : procInMap.entrySet()) {
			Element port = doc.createElementNS(T2FLOW_NS, "port");
			port.setAttribute("name", item.getKey());
			port.setAttribute("depth", item.getValue().toString());
			if (item.getValue() == 0)
				crosses.add(port);
			else
				dots.add(port);
		}
		// Apply the correct operations
		if (dots.size() > 1) {
			Element dot = doc.createElementNS(T2FLOW_NS, "dot");
			for (Element item : dots)
				dot.appendChild(item);
			crosses.add(dot);
		} else if (!dots.isEmpty())
			crosses.add(dots.get(0));
		Element cross = doc.createElementNS(T2FLOW_NS, "cross");
		for (Element item : crosses)
			cross.appendChild(item);
		iterationStrategy.appendChild(cross);

		return created;
	}

	Set<String> spliceOutputs(@NonNull Element processor,
			@NonNull Element outer, @NonNull Element inner) {
		Document doc = outer.getOwnerDocument();
		Set<String> created = new HashSet<String>();

		Element procOutputs = getChild(processor, "outputPorts");
		Map<String, Element> procOutputMap = new HashMap<String, Element>();
		for (Element procPort : children(procOutputs, "port"))
			procOutputMap.put(getChild(procPort, "name").getTextContent(),
					procPort);
		Element outputMap = getChild(processor, "activities", "activity", "outputMap");
		for (Element innerPort : children(getChild(inner, "outputPorts"),
				"port")) {
			String name = getChild(innerPort, "name").getTextContent();
			if (procOutputMap.containsKey(name))
				continue;

			Element port = doc.createElementNS(T2FLOW_NS, "port");
			Element e;
			e = doc.createElementNS(T2FLOW_NS, "name");
			e.setTextContent(name);
			port.appendChild(e);
			e = doc.createElementNS(T2FLOW_NS, "depth");
			e.setTextContent("0");
			port.appendChild(e);
			e = doc.createElementNS(T2FLOW_NS, "granularDepth");
			e.setTextContent("0");
			port.appendChild(e);
			procOutputs.appendChild(port);

			e = doc.createElementNS(T2FLOW_NS, "map");
			e.setAttribute("from", name);
			e.setAttribute("to", name);
			outputMap.appendChild(e);

			created.add(name);
		}

		return created;
	}

	@NonNull
	Element getInnerMasterAndSplice(@NonNull Element executablePlan,
			@NonNull Element wrap) throws NoCreateException {
		Element innerMaster = null;
		for (Element df : children(executablePlan, "dataflow")) {
			if (df.getAttribute("role").equals("top")) {
				innerMaster = df;
				df.setAttribute("role", "nested");
			}
			wrap.appendChild(wrap.getOwnerDocument().adoptNode(df));
		}
		if (innerMaster == null)
			throw new NoCreateException(
					"executable plan workflow had no toplevel dataflow");
		return innerMaster;
	}

	@NonNull
	Element getOuterMaster(@NonNull Element wrap) throws NoCreateException {
		for (Element df : children(wrap, "dataflow"))
			if (df.getAttribute("role").equals("top"))
				return df;
		throw new NoCreateException(
				"template workflow had no toplevel dataflow");
	}

	@NonNull
	Element spliceSubworkflowProcessor(@NonNull Element outer,
			@NonNull Element inner) throws NoCreateException {
		for (Element processor : children(getChild(outer, "processors"),
				"processor")) {
			if (!getChild(processor, "name").getTextContent().equals(
					SPLICE_PROCESSOR_NAME))
				continue;
			Element activity = getChild(processor, "activities", "activity");
			Element type = getChild(activity, "raven", "artifact");
			if (type.getTextContent().equals("dataflow-activity")) {
				Element dfConfig = getChild(activity, "configBean", "dataflow");
				dfConfig.setAttribute("ref", inner.getAttribute("id"));
				return processor;
			}
		}
		throw new NoCreateException("no processor splice point");
	}
}
