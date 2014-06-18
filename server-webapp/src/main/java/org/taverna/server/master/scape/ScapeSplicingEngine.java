package org.taverna.server.master.scape;

import static java.lang.String.format;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.scape.DOMUtils.attrs;
import static org.taverna.server.master.scape.DOMUtils.branch;
import static org.taverna.server.master.scape.DOMUtils.leaf;
import static org.taverna.server.master.scape.XPaths.DATALINK_FROM_PROCESSOR;
import static org.taverna.server.master.scape.XPaths.INPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.ITERATION_STRATEGY;
import static org.taverna.server.master.scape.XPaths.NAMED_PORT;
import static org.taverna.server.master.scape.XPaths.NAMED_PROCESSOR;
import static org.taverna.server.master.scape.XPaths.OUTPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.PORT;
import static org.taverna.server.master.scape.XPaths.PORT_DEPTH;
import static org.taverna.server.master.scape.XPaths.PORT_NAME;
import static org.taverna.server.master.scape.XPaths.REQUIRE_NESTED;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Holder;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.common.Workflow;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The splicing engine that turns the workflow part of a Preservation Action
 * Plan into a full, useful Taverna Workflow that we can then enact.
 * 
 * @author Donal Fellows
 * 
 */
public class ScapeSplicingEngine extends SplicingEngine {
	private static final Log log = getLog("Taverna.Server.WorkflowSplicing.Scape");
	private static final Pattern PORT_INFO_EXTRACT = Pattern
			.compile("^measure_([a-zA-Z0-9]+)_([a-zA-Z0-9]+)$");

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
	public static final String DUMMY_PROCESSOR_NAME = "ignore";
	private static final String TAVERNA_SPLICER_URL = "http://ns.taverna.org.uk/taverna-server/splicing";
	private static final String TAVERNA_SUBJECT_PROPERTY = TAVERNA_SPLICER_URL
			+ "#OutputPortSubject";
	private static final String TAVERNA_TYPE_PROPERTY = TAVERNA_SPLICER_URL
			+ "#OutputPortType";
	private static final String SCAPE_URL = "http://purl.org/DP/components";
	private static final String SCAPE_ACCEPTS_PROPERTY = SCAPE_URL + "#accepts";
	private static final String SCAPE_PROVIDES_PROPERTY = SCAPE_URL
			+ "#provides";
	private static final String SCAPE_SOURCE_OBJECT = SCAPE_URL
			+ "#SourceObject";
	private static final String SCAPE_TARGET_OBJECT = SCAPE_URL
			+ "#TargetObject";

	private final String baseSubject;

	public static enum Model {
		One2OneNoSchema("1to1"), One2OneSchema("1to1_schema"), Characterise(
				"characterise"), CharacteriseSchema("characterise_schema");
		// FIXME Write these alternate schemas
		@Nonnull
		private final String key;

		private Model(@Nonnull String key) {
			this.key = key;
		}

		@Nonnull
		public String getKey() {
			return key;
		}
	}

	private String repository = "http://www.myexperiment.org/";
	private String utilityFamily = "SCAPE Utility Components";
	private String builderName = "MeasuresDocBuilder";
	private String builderVersion = "3";
	private String joinerName = "MeasuresDocCombiner";
	private String joinerVersion = "1";
	private String dummyProcessorName;
	private String wrapperDirectory;

	public ScapeSplicingEngine() throws ParserConfigurationException {
		super(log);
		baseSubject = "urn:" + UUID.randomUUID();
	}

	/** Where components are stored. Probably myExperiment. */
	public void setComponentRepositoryAddress(String address)
			throws MalformedURLException {
		// Check if sane URI and URL!
		URI.create(address).toURL();
		repository = address;
	}

	/** The name of the component family for utility components. */
	public void setUtilityComponentFamily(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException("family name must be non-empty");
		utilityFamily = name;
	}

	/** The name of the component used to build atomic assessment documents. */
	public void setBuilderComponentName(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException(
					"component name must be non-empty");
		builderName = name;
	}

	/** The version of the component used to build atomic assessment documents. */
	public void setBuilderComponentVersion(int version) {
		if (version < 1)
			throw new IllegalArgumentException("version must be positive");
		builderVersion = Integer.toString(version);
	}

	/** The name of the component used to join assessment documents. */
	public void setJoinerComponentName(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException(
					"component name must be non-empty");
		joinerName = name;
	}

	/** The version of the component used to join assessment documents. */
	public void setJoinerComponentVersion(int version) {
		if (version < 1)
			throw new IllegalArgumentException("version must be positive");
		joinerVersion = Integer.toString(version);
	}

	/** The name of the processor to be deleted. */
	@Required
	public void setDummyProcessorName(String name) {
		dummyProcessorName = name;
	}

	/** The name of the directory holding wrapping workflows. */
	public void setWrapperDirectory(String directory) {
		if (directory == null || directory.isEmpty()
				|| !new File(directory).isDirectory())
			wrapperDirectory = null;
		wrapperDirectory = directory;
	}

	@Nonnull
	public Workflow constructWorkflow(@Nonnull Element executablePlan,
			@Nonnull Model model) throws Exception {
		return constructWorkflow(executablePlan, model.getKey());
	}

	@Nonnull
	synchronized Element getWrapperInstance(@Nonnull Model model)
			throws IOException, ParserConfigurationException, SAXException {
		return getWrapperInstance(model.getKey());
	}

	@Override
	protected void connectInnerToOuter(@Nonnull Element topDataflow,
			@Nonnull Element linkingDataflow,
			@Nonnull Element insertedDataflow,
			@Nonnull Set<String> createdInputNames,
			@Nonnull Set<String> createdOutputNames) throws Exception {
		connectInnerInputsToTop(topDataflow, linkingDataflow, insertedDataflow,
				createdInputNames);
		@Nonnull
		Set<String> linkingOutputNames = connectInnerOutputsToTop(topDataflow,
				insertedDataflow, linkingDataflow, createdOutputNames);
		concatenateDocuments(topDataflow, linkingOutputNames);
	}

	private void combineMetricDocuments(@Nonnull Element topMaster,
			@Nonnull Set<String> subjectPorts,
			@Nonnull Holder<String> finalProcessor,
			@Nonnull Holder<String> finalPort) throws Exception {
		int counter = 0;
		String currentProcessor = null, currentPort = null;
		for (String portName : subjectPorts) {
			if (portName == null)
				continue;
			if (currentPort == null) {
				currentProcessor = linkingDataflowName;
				currentPort = portName;
				continue;
			}
			String procName = "CONCAT" + ++counter;
			makeComponent(topMaster, procName, repository, utilityFamily,
					joinerName, joinerVersion, new String[] {
							"metricDocument1", "metricDocument2" },
					new String[] { "combinedMetricDocument" });
			datalink(topMaster, currentProcessor, currentPort, procName,
					"metricDocument1");
			datalink(topMaster, linkingDataflowName, portName, procName,
					"metricDocument2");
			currentProcessor = procName;
			currentPort = "combinedMetricDocument";
		}
		finalProcessor.value = currentProcessor;
		finalPort.value = currentPort;
	}

	// FIXME put splicing inside the ObjectTransform
	private void concatenateDocuments(@Nonnull Element topMaster,
			@Nonnull Set<String> subjectPorts) throws Exception {
		Holder<String> sourceProcessor = new Holder<>(), sourcePort = new Holder<>();
		combineMetricDocuments(topMaster, subjectPorts, sourceProcessor,
				sourcePort);
		boolean removedWorkflow = false;
		for (Element e : select(topMaster, NAMED_PROCESSOR, dummyProcessorName))
			removedWorkflow |= e.getParentNode().removeChild(e) != null;

		if (sourcePort.value != null)
			for (Element e : select(topMaster, DATALINK_FROM_PROCESSOR,
					dummyProcessorName)) {
				datalink(topMaster, sourceProcessor.value, sourcePort.value,
						text(e, "t:sink/t:processor"), text(e, "t:sink/t:port"));
				e.getParentNode().removeChild(e);
			}
		else if (removedWorkflow) {
			log.warn("failed to replace datalink from removed processor \""
					+ dummyProcessorName
					+ "\" with real link: workflow WILL FAIL");
			throw new Exception("no link replacement");
		}
	}

	protected boolean getSubjectType(String name, Element port,
			Holder<String> subject, Holder<String> type) {
		try {
			String portName = text(port, "t:name");
			SemanticAnnotationParser p = getAnnotations(port);
			if (getSubjectTypeFromAnnotation(portName, p, subject, type))
				return true;
		} catch (XPathExpressionException e) {
			// Ignore; fall back to names
		}
		Matcher m = PORT_INFO_EXTRACT.matcher(name);
		if (!m.matches())
			return false;
		subject.value = m.group(1);
		type.value = m.group(2);
		return true;
	}

	private WeakHashMap<Element, SemanticAnnotationParser> annotationCache = new WeakHashMap<>();

	private SemanticAnnotationParser getAnnotations(
			@Nonnull Element workflowElement) {
		SemanticAnnotationParser ann = annotationCache.get(workflowElement);
		if (ann != null)
			return ann;
		try {
			List<Element> turtle = select(
					workflowElement,
					".//annotationBean[@class=\"%s\"][mimeType=\"text/rdf+n3\"]/content",
					"net.sf.taverna.t2.annotation.annotationbeans.SemanticAnnotation");
			if (turtle != null && !turtle.isEmpty())
				ann = new SemanticAnnotationParser(baseSubject, turtle);
		} catch (RuntimeException | XPathExpressionException e) {
		}
		if (ann == null)
			ann = new SemanticAnnotationParser(baseSubject, "");
		annotationCache.put(workflowElement, ann);
		return ann;
	}

	private boolean getSubjectTypeFromAnnotation(String name, SemanticAnnotationParser ah,
			Holder<String> subject, Holder<String> type) {
		try {
			subject.value = ah.getProperty(TAVERNA_SUBJECT_PROPERTY);
			try {
				if (subject.value == null)
					subject.value = "TargetObject";
				else
					subject.value = new URI(subject.value).getFragment().trim();
			} catch (URISyntaxException | NullPointerException e) {
			}
			type.value = ah.getProperty(TAVERNA_TYPE_PROPERTY);
			if (type.value == null) {
				String provided = ah.getProperty(SCAPE_PROVIDES_PROPERTY);
				if (provided != null && !provided.equals(SCAPE_TARGET_OBJECT))
					type.value = provided;
			}
			if (!subject.value.isEmpty() && type.value != null
					&& !type.value.isEmpty()) {
				log.info(String.format("port %s has subject %s and type %s",
						name, subject.value, type.value));
				return true;
			}
		} catch (RuntimeException e) {
			log.error("failed to construct and extract info from "
					+ "semantic model for port " + name, e);
		}
		log.info("failed to get subject and type for port " + name);
		subject.value = null;
		type.value = null;
		return false;
	}

	@Nonnull
	private Set<String> connectInnerOutputsToTop(@Nonnull Element topMaster,
			@Nonnull Element innerMaster, @Nonnull Element outerMaster,
			@Nonnull Set<String> createdOut) throws Exception {
		for (Element out: select(innerMaster, OUTPUT_PORT_LIST + "/" + PORT)) {
			String name = text(out, PORT_NAME);
			SemanticAnnotationParser ann = getAnnotations(out);
			if (SCAPE_TARGET_OBJECT.equals(ann.getProperty(SCAPE_PROVIDES_PROPERTY))) {
				datalink(outerMaster, innerProcessorName, name, null, "outputFile");
				break;
			}
		}
		class Pair {
			String type, name;

			Pair(Holder<String> type, String name) {
				this.type = type.value;
				this.name = name;
			}
		}
		Map<String, List<Pair>> types = new HashMap<>();
		Set<String> topPortSet = new HashSet<>();
		Element outPorts = get(outerMaster, OUTPUT_PORT_LIST);
		Element topProcessor = get(topMaster, NAMED_PROCESSOR,
				linkingDataflowName);
		Element topPorts = get(topProcessor, OUTPUT_PORT_LIST);
		for (String out : createdOut) {
			Holder<String> subject = new Holder<>();
			Holder<String> type = new Holder<>();
			if (!getSubjectType(out,
					get(innerMaster, OUTPUT_PORT_LIST + NAMED_PORT, out),
					subject, type))
				continue;
			if (!types.containsKey(subject.value)) {
				types.put(subject.value, new ArrayList<Pair>());
				String mp = "measures_" + subject.value;
				topPortSet.add(mp);
				Element outp = branch(outPorts, "port");
				leaf(outp, "name", mp);
				branch(outp, "annotations");
				if (topPorts != null)
					port(topPorts, mp, 0, 0);
				mapOutput(topProcessor, mp);
			}
			types.get(subject.value).add(new Pair(type, out));
		}
		Element links = get(outerMaster, "t:datalinks");
		for (String subject : types.keySet()) {
			String dbName = "SCAPE_Metric_Document_Builder_" + subject;
			String typesName = "TYPES_" + subject;
			String subjectName = "SUBJECT_" + subject;

			StringBuilder sb = new StringBuilder();
			String sep = "";
			for (Pair type : types.get(subject)) {
				Element link = branch(links, "datalink");
				Element sink = attrs(branch(link, "sink"), "type", "merge");
				leaf(sink, "processor", dbName);
				leaf(sink, "port", "values");
				Element source = attrs(branch(link, "source"), "type",
						"processor");
				leaf(source, "processor", innerProcessorName);
				leaf(source, "port", type.name);
				sb.append(sep).append(type.type);
				sep = ",";
			}
			makeConstant(outerMaster, subjectName, subject);
			makeConstant(outerMaster, typesName, sb.toString());

			// The port names below
			final String VALUES = "values";
			final String SUBJECT = "subject";
			final String TYPES = "types";
			final String OUT = "metricDocument";

			Element docBuilder = makeComponent(outerMaster, dbName, repository,
					utilityFamily, builderName, builderVersion, new String[] {
							TYPES, VALUES, SUBJECT }, new String[] { OUT });
			Element inputPortDepth = get(docBuilder, INPUT_PORT_LIST
					+ NAMED_PORT + "/" + PORT_DEPTH, VALUES);
			if (inputPortDepth != null)
				inputPortDepth.setTextContent("1");
			Element istrat = get(docBuilder,
					"t:iterationStrategyStack/t:iteration/t:strategy");
			Element cross = branch(istrat, "cross");
			Element values = get(istrat, "t:dot/t:port[@name = \"%s\"]", VALUES);
			if (values != null) {
				values.setAttribute("depth", "1");
				cross.appendChild(values);
			}
			cross.appendChild(get(istrat, "t:dot"));

			datalink(outerMaster, subjectName, "value", dbName, SUBJECT);
			datalink(outerMaster, typesName, "value", dbName, TYPES);
			datalink(outerMaster, dbName, OUT, null, "measures_" + subject);
		}
		return topPortSet;
	}

	private void connectInnerInputsToTop(@Nonnull Element topMaster,
			@Nonnull Element outerMaster, @Nonnull Element innerMaster,
			@Nonnull Set<String> createdIn) throws Exception {
		Element outProc = get(outerMaster, NAMED_PROCESSOR + REQUIRE_NESTED,
				innerProcessorName);
		Element top = getMaybe(topMaster, NAMED_PROCESSOR + REQUIRE_NESTED,
				linkingDataflowName);
		if (top == null)
			throw new Exception(format("no top context!: " + NAMED_PROCESSOR
					+ REQUIRE_NESTED, linkingDataflowName));
		Element cross = get(top, ITERATION_STRATEGY + "/t:cross");
		for (String in : createdIn) {
			if (in == null)
				continue;
			Element inPort = get(innerMaster, INPUT_PORT_LIST + NAMED_PORT, in);
			get(outerMaster, INPUT_PORT_LIST).appendChild(
					inPort.cloneNode(true));
			port(get(outProc, INPUT_PORT_LIST), in, 0, null);
			mapInput(outProc, in);
			datalink(outerMaster, null, in, innerProcessorName, in);
			attrs(branch(cross, "port"), "name", in, "depth", "0");
			port(get(top, INPUT_PORT_LIST), in, 0, null);
			mapInput(top, in);
			branch(port(get(topMaster, INPUT_PORT_LIST), in, 0, 0),
					"annotations");
			datalink(topMaster, null, in, linkingDataflowName, in);
		}
		for (Element in: select(innerMaster, INPUT_PORT_LIST + "/" + PORT)) {
			String name = text(in, PORT_NAME);
			SemanticAnnotationParser ann = getAnnotations(in);
			if (SCAPE_SOURCE_OBJECT.equals(ann.getProperty(SCAPE_ACCEPTS_PROPERTY))) {
				datalink(outerMaster, null, "inputFile", innerProcessorName, name);
				break;
			}
		}
	}

	@Nonnull
	@Override
	protected String loadWrapperInstance(@Nonnull String name)
			throws IOException {
		if (wrapperDirectory != null) {
			File f = new File(wrapperDirectory, name);
			if (f.exists() && f.isFile())
				return FileUtils.readFileToString(f);
		}
		return super.loadWrapperInstance(name);
	}

	public static void main(String... args) throws Exception {
		String from = (args.length > 0 ? new URL(new URL("file:/"), args[0])
				: ScapeSplicingEngine.class.getResource("PAP_Example.t2flow"))
				.toString();
		String to = args.length > 1 ? args[1] : "/tmp/PAP_out.t2flow";
		System.out.println(format("transformation from %s to %s", from, to));
		ScapeSplicingEngine e = new ScapeSplicingEngine();
		e.setWrapperPrefix("SCAPE_outer_");
		e.setLinkingDataflowName(CONTAINER_NAME);
		e.setInnerProcessorName(SPLICE_PROCESSOR_NAME);
		e.setDummyProcessorName(DUMMY_PROCESSOR_NAME);
		@Nonnull
		Element executablePlan = e.parse(new InputSource(from));

		System.out.println("setup ok");

		Workflow w = e.constructWorkflow(executablePlan, Model.One2OneNoSchema);

		System.out.println("transform ok");

		IOUtils.write(e.write(w.getWorkflowRoot()), new FileOutputStream(to),
				"UTF-8");
		System.out.println("wrote results to " + to);
	}
}
