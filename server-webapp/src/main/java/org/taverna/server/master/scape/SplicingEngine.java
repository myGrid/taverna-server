package org.taverna.server.master.scape;

import static java.lang.Math.min;
import static org.taverna.server.master.rest.scape.Namespaces.T2FLOW_NS;
import static org.taverna.server.master.scape.DOMUtils.attrs;
import static org.taverna.server.master.scape.DOMUtils.branch;
import static org.taverna.server.master.scape.DOMUtils.cls;
import static org.taverna.server.master.scape.DOMUtils.config;
import static org.taverna.server.master.scape.DOMUtils.leaf;
import static org.taverna.server.master.scape.XPaths.INPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.ITERATION_STRATEGY;
import static org.taverna.server.master.scape.XPaths.NAMED_PROCESSOR;
import static org.taverna.server.master.scape.XPaths.OUTPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.PORT;
import static org.taverna.server.master.scape.XPaths.PORT_DEPTH;
import static org.taverna.server.master.scape.XPaths.PORT_NAME;
import static org.taverna.server.master.scape.XPaths.REQUIRE_NESTED;
import static org.w3c.dom.Node.ELEMENT_NODE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.NoCreateException;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public abstract class SplicingEngine extends XPathSupport {
	private static final String T2_CORE_PKG = "net.sf.taverna.t2.core";
	private static final String ACTIVITY_CONFIG_PKG = "net.sf.taverna.t2.workflowmodel.processor.activity.config.";
	private static final String DISPATCH_LAYERS_PKG = "net.sf.taverna.t2.workflowmodel.processor.dispatch.layers.";
	private static final String STRING_CONSTANT_PKG = "net.sf.taverna.t2.activities.stringconstant.";
	private static final String BEANSHELL_PKG = "net.sf.taverna.t2.activities.beanshell.";
	private static final String COMPONENT_PKG = "net.sf.taverna.t2.component.";

	private final Log log;
	private final Map<String, String> wrapperContents = new HashMap<String, String>();
	private final DocumentBuilderFactory docBuilderFactory;

	protected String wrapperPrefix;
	protected String innerProcessorName;
	protected String linkingDataflowName;

	public SplicingEngine(Log log) throws ParserConfigurationException {
		super(log, "", "", "t", T2FLOW_NS);
		this.log = log;
		docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setNamespaceAware(true);
	}

	/**
	 * The prefix for workflow file names used when working out what to load
	 * wrappers from. The suffix is always <tt>.t2flow</tt>.
	 */
	@Required
	public void setWrapperPrefix(String wrapperPrefix) {
		this.wrapperPrefix = wrapperPrefix;
	}

	/**
	 * The name of the processor that will have the injected workflow spliced
	 * into it.
	 */
	@Required
	public void setInnerProcessorName(String name) {
		innerProcessorName = name;
	}

	/**
	 * The name of the dataflow (sub-workflow) that contains the inner
	 * processor.
	 */
	@Required
	public void setLinkingDataflowName(String name) {
		linkingDataflowName = name;
	}

	Element parse(InputSource source) throws SAXException, IOException,
			ParserConfigurationException {
		DocumentBuilder db = docBuilderFactory.newDocumentBuilder();
		db.setErrorHandler(new ErrorHandler() {
			@Override
			public void error(SAXParseException error) throws SAXException {
				log.error(
						"problem with parsing document: " + error.getMessage(),
						error);
			}

			@Override
			public void fatalError(SAXParseException fatal) throws SAXException {
				log.fatal(
						"major problem with parsing document: "
								+ fatal.getMessage(), fatal);
			}

			@Override
			public void warning(SAXParseException warning) throws SAXException {
				log.warn(warning.getMessage());
			}
		});
		return db.parse(source).getDocumentElement();
	}

	@Nonnull
	protected final synchronized Element getWrapperInstance(
			@Nonnull String modelName) throws IOException,
			ParserConfigurationException, SAXException {
		if (!wrapperContents.containsKey(modelName))
			wrapperContents.put(modelName, loadWrapperInstance(wrapperPrefix
					+ modelName + ".t2flow"));
		return parse(new InputSource(new StringReader(
				wrapperContents.get(modelName))));
	}

	@Nonnull
	protected String loadWrapperInstance(@Nonnull String fileName)
			throws IOException {
		URL resource = getClass().getResource(fileName);
		if (resource == null)
			throw new FileNotFoundException("no such resource: " + fileName);
		return IOUtils.toString(resource);
	}

	/**
	 * Ugly hack to force the realization of the values of attributes, working
	 * around a bug <i>somewhere</i> in Java. This is observed in Java 6.
	 * 
	 * @param base
	 *            The element to start the search for attributes from.
	 * @throws XPathExpressionException
	 *             If anything fails (not expected).
	 */
	private void realizeAttrs(Element base) throws XPathExpressionException {
		for (Node n : selectNodes(base, "//@*"))
			((Attr) n).getValue();
	}

	/**
	 * Constructs a full workflow from the workflow fragment extracted from a
	 * preservation action plan, where the outer workflow conforms to a given
	 * model.
	 * 
	 * @param executablePlan
	 *            The workflow fragment
	 * @param modelName
	 *            The name of the model file to conform to
	 * @return The wrapped constructed workflow
	 * @throws Exception
	 *             If anything goes wrong
	 */
	@Nonnull
	public Workflow constructWorkflow(@Nonnull Element executablePlan,
			@Nonnull String modelName) throws Exception {
		Element wrap = getWrapperInstance(modelName);

		/*
		 * CRITICAL! Work around Java DOM bug! Ensure all attributes are
		 * realized!
		 */
		realizeAttrs(executablePlan);
		realizeAttrs(wrap);

		// Splice in dataflows
		Element topMaster = getTop(wrap);
		Element outerMaster = getOuterMaster(wrap);
		Element innerMaster = getInnerMasterAndSplice(executablePlan, wrap);
		// Do not use executablePlan from here on!

		// Splice in processor
		Element dataflowProcessor = spliceSubworkflowProcessor(outerMaster,
				innerMaster);
		Set<String> createdIn = spliceInputs(dataflowProcessor, outerMaster,
				innerMaster);
		Set<String> createdOut = spliceOutputs(dataflowProcessor, outerMaster,
				innerMaster);

		connectInnerToOuter(topMaster, outerMaster, innerMaster, createdIn,
				createdOut);

		// Splice into POJO
		Workflow w = new Workflow();
		w.content = new Element[] { wrap };
		return w;
	}

	/**
	 * Constructs the linking dataflow between the inserted and top dataflows,
	 * and connects any inputs and outputs produced on the linking dataflow to
	 * the right places in the top dataflow.
	 * 
	 * @param topDataflow
	 *            The main dataflow of the workflow.
	 * @param linkingDataflow
	 *            The dataflow that contains the coupling machinery.
	 * @param insertedDataflow
	 *            The user-supplied inner dataflow, already in the main
	 *            document.
	 * @param createdInputNames
	 *            The names of the input ports added to the inserted nested
	 *            dataflow processor (beyond the model) during the import
	 *            processing.
	 * @param createdOutputNames
	 *            The names of the output ports added to the inserted nested
	 *            dataflow processor (beyond the model) during the import
	 *            processing.
	 * @throws Exception
	 *             If anything goes wrong.
	 */
	protected abstract void connectInnerToOuter(@Nonnull Element topDataflow,
			@Nonnull Element linkingDataflow,
			@Nonnull Element insertedDataflow,
			@Nonnull Set<String> createdInputNames,
			@Nonnull Set<String> createdOutputNames) throws Exception;

	@Nonnull
	protected static Element port(@Nonnull Element container,
			@Nonnull String name, @Nullable Integer depth,
			@Nullable Integer granularDepth) {
		return DOMUtils.port(container, name,
				depth != null ? Integer.toString(depth) : null,
				granularDepth != null ? Integer.toString(granularDepth) : null);
	}

	protected Element datalink(@Nonnull Element dataflow,
			@Nullable String fromProc, @Nonnull String fromPort,
			@Nullable String toProc, @Nonnull String toPort) throws Exception {
		return DOMUtils.datalink(get(dataflow, "t:datalinks"), fromProc,
				fromPort, toProc, toPort);
	}

	protected void mapInput(Element processor, String name)
			throws XPathExpressionException {
		Element inMap = get(processor, "t:activities/t:activity/t:inputMap");
		attrs(branch(inMap, "map"), "from", name, "to", name);
	}

	protected void mapOutput(Element processor, String name)
			throws XPathExpressionException {
		Element outMap = get(processor, "t:activities/t:activity/t:outputMap");
		attrs(branch(outMap, "map"), "from", name, "to", name);
	}

	protected Element makeConstant(Element dataflow, String name, String value)
			throws Exception {
		Element processor = branch(get(dataflow, "t:processors"), "processor");
		leaf(processor, "name", name);
		branch(processor, "inputPorts");
		port(branch(processor, "outputPorts"), "value", 0, 0);
		branch(processor, "annotations");
		{
			Element activity = branch(processor, "activities", "activity");
			cls(activity, "net.sf.taverna.t2.activities",
					"stringconstant-activity", "1.4", STRING_CONSTANT_PKG
							+ "StringConstantActivity");
			branch(activity, "inputMap");
			attrs(branch(activity, "outputMap", "map"), "from", "value", "to",
					"value");
			Element config = config(activity, STRING_CONSTANT_PKG
					+ "StringConstantConfigurationBean");
			leaf(config, "value", value);
			branch(activity, "annotations");
		}
		dispatchLayer(processor, 1, 0);
		branch(processor, "iterationStrategyStack", "iteration", "strategy");
		return processor;
	}

	protected Element makeBeanshell(Element dataflow, String name,
			String script, String[] inputs, String[] outputs) throws Exception {
		Element processor = branch(get(dataflow, "t:processors"), "processor");
		leaf(processor, "name", name);
		{
			Element ports = branch(processor, "inputPorts");
			for (String in : inputs)
				port(ports, in, 0, null);
			ports = branch(processor, "outputPorts");
			for (String out : outputs)
				port(ports, out, 0, 0);
		}
		branch(processor, "annotations");
		{
			Element activity = branch(processor, "activities", "activity");
			cls(activity, "net.sf.taverna.t2.activities", "beanshell-activity",
					"1.4", BEANSHELL_PKG + "BeanshellActivity");
			Element map = branch(activity, "inputMap");
			for (String in : inputs)
				attrs(branch(map, "map"), "from", in, "to", in);
			map = branch(activity, "outputMap");
			for (String out : outputs)
				attrs(branch(map, "map"), "from", out, "to", out);
			Element config = config(activity, BEANSHELL_PKG
					+ "BeanshellActivityConfigurationBean");
			Element inputsConfig = branch(config, "inputs");
			for (String in : inputs) {
				Element bean = branch(inputsConfig, ACTIVITY_CONFIG_PKG
						+ "ActivityInputPortDefinitionBean");
				leaf(bean, "name", in);
				leaf(bean, "depth", "0");
				leaf(branch(bean, "mimeTypes"), "string", "text/plain");
				branch(bean, "handledReferenceSchemes");
				leaf(bean, "translatedElementType", "java.lang.String");
				leaf(bean, "allowsLiteralValues", "true");
			}
			Element outputsConfig = branch(config, "inputs");
			for (String out : outputs) {
				Element bean = branch(outputsConfig, ACTIVITY_CONFIG_PKG
						+ "ActivityOutputPortDefinitionBean");
				leaf(bean, "name", out);
				leaf(bean, "depth", "0");
				branch(bean, "mimeTypes");
				leaf(bean, "granularDepth", "0");
			}
			leaf(config, "classLoaderSharing", "workflow");
			branch(config, "localDependencies");
			branch(config, "artifactDependencies");
			leaf(config, "script", script);
			branch(config, "dependencies");
			branch(activity, "annotations");
		}
		dispatchLayer(processor, 1, 0);
		Element strategy = branch(processor, "iterationStrategyStack",
				"iteration", "strategy");
		if (inputs.length > 0) {
			Element dot = branch(strategy, "dot");
			for (String in : inputs)
				attrs(branch(dot, "port"), "name", in, "depth", "0");
		}
		return processor;
	}

	/**
	 * Note: input ports are all depth 0 by default, and are all dotted
	 * together!
	 */
	protected Element makeComponent(Element dataflow, String name, String repo,
			String family, String component, String version, String[] inputs,
			String[] outputs) throws Exception {
		Element processor = branch(get(dataflow, "t:processors"), "processor");
		leaf(processor, "name", name);
		{
			Element ports = branch(processor, "inputPorts");
			for (String in : inputs)
				port(ports, in, 0, null);
			ports = branch(processor, "outputPorts");
			for (String out : outputs)
				port(ports, out, 0, 0);
		}
		branch(processor, "annotations");
		{
			Element activity = branch(processor, "activities", "activity");
			cls(activity, "net.sf.taverna.t2.component", "component-activity",
					"1.1.2", COMPONENT_PKG + "ComponentActivity");
			Element map = branch(activity, "inputMap");
			for (String in : inputs)
				attrs(branch(map, "map"), "from", in, "to", in);
			map = branch(activity, "outputMap");
			for (String out : outputs)
				attrs(branch(map, "map"), "from", out, "to", out);
			Element config = config(activity, COMPONENT_PKG
					+ "ComponentActivityConfigurationBean");
			leaf(config, "registryBase", repo);
			leaf(config, "familyName", family);
			leaf(config, "componentName", component);
			leaf(config, "componentVersion", version);
			branch(activity, "annotations");
		}
		dispatchLayer(processor, 1, 0);
		Element strategy = branch(processor, "iterationStrategyStack",
				"iteration", "strategy");
		if (inputs.length > 0) {
			Element dot = branch(strategy, "dot");
			for (String in : inputs)
				attrs(branch(dot, "port"), "name", in, "depth", "0");
		}
		return processor;
	}

	/**
	 * Make the dispatch layer for a processor. Assumes that it is being called
	 * at the correct moment during construction; does not try to get ordering
	 * within the processor declaration right otherwise.
	 * 
	 * @param processor
	 *            The processor to make the layer for.
	 * @param parallelSize
	 *            The number of parallel invocations to support.
	 * @param retries
	 *            The number of times to retry. The backoff strategy is left set
	 *            to the default.
	 */
	protected static void dispatchLayer(Element processor, int parallelSize,
			int retries) {
		Element dispatch = branch(processor, "dispatchStack");

		Element parallelize = branch(dispatch, "dispatchLayer");
		cls(parallelize, T2_CORE_PKG, "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Parallelize");
		leaf(config(parallelize, DISPATCH_LAYERS_PKG + "ParallelizeConfig"),
				"maxJobs", Integer.toString(parallelSize));

		Element errorBounce = branch(dispatch, "dispatchLayer");
		cls(errorBounce, T2_CORE_PKG, "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "ErrorBounce");
		config(errorBounce, "null");

		Element failover = branch(dispatch, "dispatchLayer");
		cls(failover, T2_CORE_PKG, "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Failover");
		config(failover, "null");

		Element retry = branch(dispatch, "dispatchLayer");
		cls(retry, T2_CORE_PKG, "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Retry");
		Element config = config(retry, DISPATCH_LAYERS_PKG + "RetryConfig");
		leaf(config, "backoffFactor", "1.0");
		leaf(config, "initialDelay", "1000");
		leaf(config, "maxDelay", "5000");
		leaf(config, "maxRetries", Integer.toString(retries));

		Element invoke = branch(dispatch, "dispatchLayer");
		cls(invoke, T2_CORE_PKG, "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Invoke");
		config(invoke, "null");
	}

	/**
	 * Connects the spliced dataflow to the input ports of the processor that
	 * "contains" it.
	 * 
	 * @param processor
	 * @param outer
	 * @param inner
	 * @return
	 * @throws XPathExpressionException
	 */
	@Nonnull
	protected Set<String> spliceInputs(@Nonnull Element processor,
			@Nonnull Element outer, @Nonnull Element inner)
			throws XPathExpressionException {
		Document doc = outer.getOwnerDocument();
		Set<String> created = new HashSet<String>();

		// Construct process ports for inputs on the inner dataflow
		Element procInputs = get(processor, INPUT_PORT_LIST);
		Map<String, Integer> procInMap = new HashMap<String, Integer>();
		for (Element pin : select(procInputs, PORT))
			procInMap.put(text(pin, PORT_NAME),
					new Integer(text(pin, PORT_DEPTH)));
		for (Element in : select(inner, INPUT_PORT_LIST + "/" + PORT)) {
			String name = text(in, PORT_NAME);
			if (procInMap.containsKey(name))
				continue;
			int d = Integer.parseInt(text(in, PORT_DEPTH));
			port(procInputs, name, d, null);
			procInMap.put(name, d);
		}

		// Construct mapping between inside and outside
		Map<String, Element> inputMapping = new HashMap<String, Element>();
		for (Element e : select(processor,
				"t:activities/t:activity/t:inputMap/t:map"))
			inputMapping.put(e.getAttribute("from"), e);
		for (String name : procInMap.keySet())
			if (!inputMapping.containsKey(name))
				mapInput(processor, name);

		// Construct iteration strategy
		Element iterationStrategy = get(processor, ITERATION_STRATEGY);
		NodeList nl = iterationStrategy.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++)
			if (nl.item(i).getNodeType() == ELEMENT_NODE)
				iterationStrategy.removeChild(nl.item(i));
		// Categorise ports by their depth
		List<Element> dots = new ArrayList<Element>();
		List<Element> crosses = new ArrayList<Element>();
		for (Entry<String, Integer> item : procInMap.entrySet()) {
			Element port = attrs(doc.createElementNS(T2FLOW_NS, "port"),
					"name", item.getKey(), "depth", item.getValue().toString());
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

	@Nonnull
	protected Set<String> spliceOutputs(@Nonnull Element processor,
			@Nonnull Element outer, @Nonnull Element inner)
			throws XPathExpressionException, DOMException {
		Set<String> created = new HashSet<String>();

		Element procOutputs = get(processor, OUTPUT_PORT_LIST);
		Map<String, Element> procOutputMap = new HashMap<String, Element>();
		for (Element procPort : select(procOutputs, PORT))
			procOutputMap.put(text(procPort, PORT_NAME), procPort);
		for (Element innerPort : select(inner, OUTPUT_PORT_LIST + "/" + PORT)) {
			String name = text(innerPort, PORT_NAME);
			if (procOutputMap.containsKey(name))
				continue;

			port(procOutputs, name, 0, 0);
			mapOutput(processor, name);
			created.add(name);
		}

		return created;
	}

	@Nonnull
	protected Element getInnerMasterAndSplice(@Nonnull Element executablePlan,
			@Nonnull Element wrap) throws NoCreateException,
			XPathExpressionException, DOMException {
		wrap.removeChild(getDataflow(wrap, innerProcessorName));
		Element innerMaster = null;
		for (Element df : select(executablePlan, "t:dataflow")) {
			if (isMatched(df, "@role = \"top\""))
				attrs(innerMaster = df, "role", "nested");
			wrap.appendChild(wrap.getOwnerDocument().adoptNode(df));
		}
		if (innerMaster == null)
			throw new NoCreateException(
					"executable plan workflow had no toplevel dataflow");
		return innerMaster;
	}

	/**
	 * Get a named dataflow from a workflow.
	 * 
	 * @param workflow
	 *            The workflow document.
	 * @param name
	 *            The name of the dataflow
	 * @return The dataflow, or <tt>null</tt> if no such workflow exists.
	 * @throws XPathExpressionException
	 */
	@Nullable
	Element getDataflow(@Nonnull Element workflow, @Nonnull String name)
			throws XPathExpressionException {
		return get(workflow, "t:dataflow[t:name = \"%s\"]",
				name.substring(0, min(20, name.length())));
	}

	@Nonnull
	private Element getOuterMaster(@Nonnull Element wrap)
			throws NoCreateException, XPathExpressionException {
		Element dataflow = getDataflow(wrap, linkingDataflowName);
		if (dataflow != null)
			return dataflow;
		throw new NoCreateException(
				"template workflow had no container dataflow (called "
						+ linkingDataflowName + ")");
	}

	@Nonnull
	private Element getTop(@Nonnull Element wrap) throws NoCreateException,
			XPathExpressionException {
		Element top = get(wrap, "t:dataflow[@role = \"top\"]");
		if (top != null)
			return top;
		throw new NoCreateException("template workflow had no top dataflow!");
	}

	@Nonnull
	private Element spliceSubworkflowProcessor(@Nonnull Element outer,
			@Nonnull Element inner) throws NoCreateException,
			XPathExpressionException, DOMException {
		for (Element processor : select(outer,
				NAMED_PROCESSOR + REQUIRE_NESTED, innerProcessorName)) {
			for (Element dataflow : select(processor,
					"t:activities/t:activity/t:configBean/t:dataflow"))
				attrs(dataflow, "ref", inner.getAttribute("id"));
			return processor;
		}
		throw new NoCreateException("no processor splice point (called "
				+ innerProcessorName + ")");
	}
}