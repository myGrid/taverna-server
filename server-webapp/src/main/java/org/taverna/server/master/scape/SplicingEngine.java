package org.taverna.server.master.scape;

import static java.lang.Math.min;
import static java.lang.String.format;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.rest.scape.Namespaces.T2FLOW_NS;
import static org.taverna.server.master.scape.DOMUtils.attrs;
import static org.taverna.server.master.scape.DOMUtils.branch;
import static org.taverna.server.master.scape.DOMUtils.cls;
import static org.taverna.server.master.scape.DOMUtils.config;
import static org.taverna.server.master.scape.DOMUtils.leaf;
import static org.w3c.dom.Node.ELEMENT_NODE;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Holder;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Value;
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class SplicingEngine extends XPathSupport {
	private static final Log log = getLog("Taverna.Server.WorkflowSplicing");
	private static final String DISPATCH_LAYERS_PKG = "net.sf.taverna.t2.workflowmodel.processor.dispatch.layers.";
	/** The name of the processor to splice. Must be a dataflow processor! */
	public static final String SPLICE_PROCESSOR_NAME = "PreservationActionPlan";
	/**
	 * The name of the processor/workflow that contains the processor to splice.
	 * Will have significant surgery performed on it.
	 */
	public static final String CONTAINER_NAME = "ObjectTransform";
	/**
	 * The name of a processor to remove from the outermost workflow.
	 */
	private static final String DUMMY_PROCESSOR_NAME = "ignore";

	private static final String OUTPUT_PORT_LIST = "t:outputPorts";
	private static final String INPUT_PORT_LIST = "t:inputPorts";
	private static final String ITERATION_STRATEGY = "t:iterationStrategyStack/t:iteration/t:strategy";
	private static final String SELECT_PROCESSOR = "t:processors/t:processor[t:name=\"%s\"]";
	private static final String REQUIRE_NESTED = "[t:activities/t:activity/t:raven/t:artifact=\"dataflow-activity\"]";
	private static final String PORT_DEPTH = "t:depth";
	private static final String PORT_NAME = "t:name";

	private final Map<Model, String> wrapperContents = new HashMap<Model, String>();
	private final DocumentBuilderFactory docBuilderFactory;

	@Value("${scape.wrapperPrefix}")
	private String wrapperPrefix;
	// TODO: take properties from config
	private String repository = "http://www.myexperiment.org/";
	private String utilityFamily = "SCAPE Utility Components";
	private String builderName = "MeasuresDocBuilder";
	private String builderVersion = "3";
	private String joinerName = "MeasuresDocCombiner";
	private String joinerVersion = "1";

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

	SplicingEngine() throws ParserConfigurationException {
		super(log, "", "", "t", T2FLOW_NS);
		docBuilderFactory = DocumentBuilderFactory.newInstance();
		docBuilderFactory.setNamespaceAware(true);
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

	public static void main(String... args) throws Exception {
		String from = (args.length > 0 ? new URL(new URL("file:/"), args[0])
				: SplicingEngine.class.getResource("PAP_Example.t2flow"))
				.toString();
		String to = args.length > 1 ? args[1] : "/tmp/PAP_out.t2flow";
		System.out.println(format("transformation from %s to %s", from, to));
		SplicingEngine e = new SplicingEngine();
		e.wrapperPrefix = "SCAPE_outer_";
		Element executablePlan = e.parse(new InputSource(from));

		System.out.println("setup ok");

		Workflow w = e.constructWorkflow(executablePlan, Model.One2OneNoSchema);

		System.out.println("transform ok");

		IOUtils.write(e.write(w.getWorkflowRoot()), new FileOutputStream(to),
				"UTF-8");
		System.out.println("wrote results to " + to);
	}

	@NonNull
	synchronized Element getWrapperInstance(@NonNull Model model)
			throws IOException, ParserConfigurationException, SAXException {
		if (!wrapperContents.containsKey(model)) {
			URL resource = getClass().getResource(
					wrapperPrefix + model.getKey() + ".t2flow");
			if (resource == null)
				throw new FileNotFoundException(wrapperPrefix + model.getKey()
						+ ".t2flow");
			wrapperContents.put(model, IOUtils.toString(resource));
		}
		return parse(new InputSource(new StringReader(
				wrapperContents.get(model))));
	}

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
	 * @param model
	 *            The model to conform to
	 * @return The wrapped constructed workflow
	 * @throws Exception
	 *             If anything goes wrong
	 */
	@NonNull
	public Workflow constructWorkflow(@NonNull Element executablePlan,
			@NonNull Model model) throws Exception {
		Element wrap = getWrapperInstance(model);

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

		connectInnerInputsToTop(topMaster, outerMaster, innerMaster, createdIn);
		concatenateDocuments(
				topMaster,
				connectInnerOutputsToTop(topMaster, innerMaster, outerMaster,
						createdOut));

		for (Element broken : select(topMaster, "//*[@encoding=\"\"]"))
			broken.setAttribute("encoding", "xstream");
		// Splice into POJO
		Workflow w = new Workflow();
		w.content = new Element[] { wrap };
		return w;
	}

	private void concatenateDocuments(Element topMaster,
			Set<String> subjectPorts) throws Exception {
		int counter = 0;
		String sourceProcessor = null, currentPort = null;
		for (String portName : subjectPorts) {
			if (currentPort == null) {
				sourceProcessor = CONTAINER_NAME;
				currentPort = portName;
				continue;
			}
			String procName = "CONCAT" + ++counter;
			makeComponent(topMaster, procName, repository, utilityFamily,
					joinerName, joinerVersion, new String[] {
							"metricDocument1", "metricDocument2" },
					new String[] { "combinedMetricDocument" });
			datalink(topMaster, sourceProcessor, currentPort, procName,
					"metricDocument1");
			datalink(topMaster, CONTAINER_NAME, portName, procName,
					"metricDocument2");
			sourceProcessor = procName;
			currentPort = "combinedMetricDocument";
		}

		for (Element e : select(topMaster,
				"t:processors/t:processor[t:name=\"%s\"]", DUMMY_PROCESSOR_NAME))
			e.getParentNode().removeChild(e);

		for (Element e : select(topMaster,
				"t:datalinks/t:datalink[t:source/t:processor=\"%s\"]",
				DUMMY_PROCESSOR_NAME)) {
			datalink(topMaster, sourceProcessor, currentPort,
					text(e, "t:sink/t:processor"), text(e, "t:sink/t:port"));
			e.getParentNode().removeChild(e);
		}
	}

	void connectInnerInputsToTop(Element topMaster, Element outerMaster,
			Element innerMaster, Set<String> createdIn) throws Exception {
		Element outProc = get(outerMaster, SELECT_PROCESSOR + REQUIRE_NESTED,
				SPLICE_PROCESSOR_NAME);
		Element top = get(topMaster, SELECT_PROCESSOR + REQUIRE_NESTED,
				CONTAINER_NAME);
		if (top == null)
			throw new Exception(format("no top context!: " + SELECT_PROCESSOR
					+ REQUIRE_NESTED, CONTAINER_NAME));
		Element cross = get(top, ITERATION_STRATEGY + "/t:cross");
		for (String in : createdIn) {
			Element inPort = get(innerMaster,
					"t:inputPorts/t:port[t:name=\"%s\"]", in);
			get(outerMaster, INPUT_PORT_LIST).appendChild(
					inPort.cloneNode(true));
			port(get(outProc, INPUT_PORT_LIST), in, 0, null);
			mapInput(outProc, in);
			datalink(outerMaster, null, in, SPLICE_PROCESSOR_NAME, in);
			attrs(branch(cross, "port"), "name", in, "depth", "0");
			port(get(top, INPUT_PORT_LIST), in, 0, null);
			mapInput(top, in);
			branch(port(get(topMaster, INPUT_PORT_LIST), in, 0, 0),
					"annotations");
			datalink(topMaster, null, in, CONTAINER_NAME, in);
		}
	}

	@NonNull
	private static Element port(@NonNull Element container,
			@NonNull String name, @Nullable Integer depth,
			@Nullable Integer granularDepth) {
		return DOMUtils.port(container, name,
				depth != null ? Integer.toString(depth) : null,
				granularDepth != null ? Integer.toString(granularDepth) : null);
	}

	private Element datalink(@NonNull Element dataflow,
			@Nullable String fromProc, @NonNull String fromPort,
			@Nullable String toProc, @NonNull String toPort) throws Exception {
		return DOMUtils.datalink(get(dataflow, "t:datalinks"), fromProc,
				fromPort, toProc, toPort);
	}

	private void mapInput(Element processor, String name)
			throws XPathExpressionException {
		Element inMap = get(processor, "t:activities/t:activity/t:inputMap");
		attrs(branch(inMap, "map"), "from", name, "to", name);
	}

	private void mapOutput(Element processor, String name)
			throws XPathExpressionException {
		Element outMap = get(processor, "t:activities/t:activity/t:outputMap");
		attrs(branch(outMap, "map"), "from", name, "to", name);
	}

	private static final Pattern PORT_INFO_EXTRACT = Pattern
			.compile("^measure_([a-zA-Z0-9]+)_([a-zA-Z0-9]+)$");

	protected boolean getSubjectType(String name, Element port,
			Holder<String> subject, Holder<String> type) {
		Matcher m = PORT_INFO_EXTRACT.matcher(name);
		if (!m.matches())
			return false;
		subject.value = m.group(1);
		type.value = m.group(2);
		return true;
	}

	Set<String> connectInnerOutputsToTop(Element topMaster,
			Element innerMaster, Element outerMaster, Set<String> createdOut)
			throws Exception {
		Map<String, List<String>> types = new HashMap<String, List<String>>();
		Set<String> topPortSet = new HashSet<String>();
		Element outPorts = get(outerMaster, OUTPUT_PORT_LIST);
		Element topProcessor = get(topMaster, SELECT_PROCESSOR, CONTAINER_NAME);
		Element topPorts = get(topProcessor, OUTPUT_PORT_LIST);
		for (String out : createdOut) {
			Holder<String> subject = new Holder<String>();
			Holder<String> type = new Holder<String>();
			if (!getSubjectType(
					out,
					get(innerMaster,
							OUTPUT_PORT_LIST + "/t:port[@name=\"%s\"]", out),
					subject, type))
				continue;
			if (!types.containsKey(subject.value)) {
				types.put(subject.value, new ArrayList<String>());
				String mp = "measures_" + subject.value;
				topPortSet.add(mp);
				Element outp = branch(outPorts, "port");
				leaf(outp, "name", mp);
				branch(outp, "annotations");
				port(topPorts, mp, 0, 0);
				mapOutput(topProcessor, mp);
			}
			types.get(subject.value).add(type.value);
		}
		Element links = get(outerMaster, "t:datalinks");
		for (String subject : types.keySet()) {
			String dbName = "SCAPE_Metric_Document_Builder_" + subject;
			String typesName = "TYPES_" + subject;
			String subjectName = "SUBJECT_" + subject;

			StringBuilder sb = new StringBuilder();
			String sep = "";
			for (String type : types.get(subject)) {
				Element link = branch(links, "datalink");
				Element sink = attrs(branch(link, "sink"), "type", "merge");
				leaf(sink, "processor", dbName);
				leaf(sink, "port", "values");
				Element source = attrs(branch(link, "source"), "type",
						"processor");
				leaf(source, "processor", SPLICE_PROCESSOR_NAME);
				leaf(source, "port", "measure_" + subject + "_" + type);
				sb.append(sep).append(type);
				sep = ",";
			}
			makeConstant(outerMaster, subjectName, subject);
			makeConstant(outerMaster, typesName, sb.toString());

			Element docBuilder = makeComponent(outerMaster, dbName, repository,
					utilityFamily, builderName, builderVersion, new String[] {
							"types", "values", "subject" },
					new String[] { "metricDocument" });
			get(docBuilder, "t:inputPorts/t:port[t:name=\"%s\"]/t:depth",
					"values").setTextContent("1");
			Element istrat = get(docBuilder,
					"t:iterationStrategyStack/t:iteration/t:strategy");
			Element cross = branch(istrat, "cross");
			Element values = get(istrat, "t:dot/t:port[@name=\"values\"]");
			values.setAttribute("depth", "1");
			cross.appendChild(values);
			cross.appendChild(get(istrat, "t:dot"));

			datalink(outerMaster, subjectName, "value", dbName, "subject");
			datalink(outerMaster, typesName, "value", dbName, "types");
			datalink(outerMaster, dbName, "metricDocument", null, "measures_"
					+ subject);
		}
		return topPortSet;
	}

	static final String STRING_CONSTANT_PKG = "net.sf.taverna.t2.activities.stringconstant.";

	private Element makeConstant(Element dataflow, String name, String value)
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

	static final String COMPONENT_PKG = "net.sf.taverna.t2.component.";

	/**
	 * Note: input ports are all depth 0 by default, and are all dotted
	 * together!
	 */
	private Element makeComponent(Element dataflow, String name, String repo,
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

	private static void dispatchLayer(Element processor, int parallelSize,
			int retries) {
		Element dispatch = branch(processor, "dispatchStack");

		Element parallelize = branch(dispatch, "dispatchLayer");
		cls(parallelize, "net.sf.taverna.t2.core", "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Parallelize");
		leaf(config(parallelize, DISPATCH_LAYERS_PKG + "ParallelizeConfig"),
				"maxJobs", Integer.toString(parallelSize));

		Element errorBounce = branch(dispatch, "dispatchLayer");
		cls(errorBounce, "net.sf.taverna.t2.core", "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "ErrorBounce");
		config(errorBounce, "null");

		Element failover = branch(dispatch, "dispatchLayer");
		cls(failover, "net.sf.taverna.t2.core", "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Failover");
		config(failover, "null");

		Element retry = branch(dispatch, "dispatchLayer");
		cls(retry, "net.sf.taverna.t2.core", "workflowmodel-impl", "1.4",
				DISPATCH_LAYERS_PKG + "Retry");
		Element config = config(retry, DISPATCH_LAYERS_PKG + "RetryConfig");
		leaf(config, "backoffFactor", "1.0");
		leaf(config, "initialDelay", "1000");
		leaf(config, "maxDelay", "5000");
		leaf(config, "maxRetries", Integer.toString(retries));

		Element invoke = branch(dispatch, "dispatchLayer");
		cls(invoke, "net.sf.taverna.t2.core", "workflowmodel-impl", "1.4",
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
	@NonNull
	Set<String> spliceInputs(@NonNull Element processor,
			@NonNull Element outer, @NonNull Element inner)
			throws XPathExpressionException {
		Document doc = outer.getOwnerDocument();
		Set<String> created = new HashSet<String>();

		// Construct process ports for inputs on the inner dataflow
		Element procInputs = get(processor, INPUT_PORT_LIST);
		Map<String, Integer> procInMap = new HashMap<String, Integer>();
		for (Element pin : select(procInputs, "t:port"))
			procInMap.put(text(pin, PORT_NAME),
					new Integer(text(pin, PORT_DEPTH)));
		for (Element in : select(inner, "t:inputPorts/t:port")) {
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

	Set<String> spliceOutputs(@NonNull Element processor,
			@NonNull Element outer, @NonNull Element inner)
			throws XPathExpressionException, DOMException {
		Set<String> created = new HashSet<String>();

		Element procOutputs = get(processor, OUTPUT_PORT_LIST);
		Map<String, Element> procOutputMap = new HashMap<String, Element>();
		for (Element procPort : select(procOutputs, "t:port"))
			procOutputMap.put(text(procPort, PORT_NAME), procPort);
		for (Element innerPort : select(inner, OUTPUT_PORT_LIST + "/t:port")) {
			String name = text(innerPort, PORT_NAME);
			if (procOutputMap.containsKey(name))
				continue;

			port(procOutputs, name, 0, 0);
			mapOutput(processor, name);
			created.add(name);
		}

		return created;
	}

	@NonNull
	Element getInnerMasterAndSplice(@NonNull Element executablePlan,
			@NonNull Element wrap) throws NoCreateException,
			XPathExpressionException, DOMException {
		wrap.removeChild(getDataflow(wrap, SPLICE_PROCESSOR_NAME));
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
	Element getDataflow(@NonNull Element workflow, @NonNull String name)
			throws XPathExpressionException {
		return get(workflow, "t:dataflow[t:name=\"%s\"]",
				name.substring(0, min(20, name.length())));
	}

	@NonNull
	Element getOuterMaster(@NonNull Element wrap) throws NoCreateException,
			XPathExpressionException {
		Element dataflow = getDataflow(wrap, CONTAINER_NAME);
		if (dataflow != null)
			return dataflow;
		throw new NoCreateException(
				"template workflow had no container dataflow (called "
						+ CONTAINER_NAME + ")");
	}

	@NonNull
	Element getTop(@NonNull Element wrap) throws NoCreateException,
			XPathExpressionException {
		Element top = get(wrap, "t:dataflow[@role=\"top\"]");
		if (top != null)
			return top;
		throw new NoCreateException("template workflow had no top dataflow!");
	}

	@NonNull
	Element spliceSubworkflowProcessor(@NonNull Element outer,
			@NonNull Element inner) throws NoCreateException,
			XPathExpressionException, DOMException {
		for (Element processor : select(outer, SELECT_PROCESSOR
				+ REQUIRE_NESTED, SPLICE_PROCESSOR_NAME)) {
			for (Element dataflow : select(processor,
					"t:activities/t:activity/t:configBean/t:dataflow"))
				attrs(dataflow, "ref", inner.getAttribute("id"));
			return processor;
		}
		throw new NoCreateException("no processor splice point (called "
				+ SPLICE_PROCESSOR_NAME + ")");
	}
}