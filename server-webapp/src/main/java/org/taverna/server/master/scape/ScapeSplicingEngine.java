package org.taverna.server.master.scape;

import static java.lang.String.format;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.scape.DOMUtils.attrs;
import static org.taverna.server.master.scape.DOMUtils.branch;
import static org.taverna.server.master.scape.DOMUtils.leaf;
import static org.taverna.server.master.scape.WorkflowConstants.SCAPE_ACCEPTS_PROPERTY;
import static org.taverna.server.master.scape.WorkflowConstants.SCAPE_PROVIDES_PROPERTY;
import static org.taverna.server.master.scape.WorkflowConstants.SCAPE_SOURCE_OBJECT;
import static org.taverna.server.master.scape.WorkflowConstants.SCAPE_TARGET_OBJECT;
import static org.taverna.server.master.scape.WorkflowConstants.TAVERNA_SUBJECT_PROPERTY;
import static org.taverna.server.master.scape.WorkflowConstants.TAVERNA_TYPE_PROPERTY;
import static org.taverna.server.master.scape.XPaths.DATALINKS;
import static org.taverna.server.master.scape.XPaths.DATALINK_FROM_PROCESSOR;
import static org.taverna.server.master.scape.XPaths.INPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.ITERATION_STRATEGY;
import static org.taverna.server.master.scape.XPaths.NAMED_PORT;
import static org.taverna.server.master.scape.XPaths.NAMED_PROCESSOR;
import static org.taverna.server.master.scape.XPaths.OUTPUT_PORT_LIST;
import static org.taverna.server.master.scape.XPaths.PORTS;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Holder;
import javax.xml.xpath.XPathException;
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
	private String workflowInputName = "inputFile"; // TODO parameterize
	private String workflowOutputName = "outputFile"; // TODO parameterize
	private String defaultSubject = "TargetObject"; // TODO parameterize

	public ScapeSplicingEngine() throws ParserConfigurationException {
		super(log);
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

			final String IN1 = "metricDocument1";
			final String IN2 = "metricDocument2";
			final String OUT = "combinedMetricDocument";

			String procName = "ScapeMetricDocumentJoiner_" + ++counter;
			makeComponent(topMaster, procName, repository, utilityFamily,
					joinerName, joinerVersion, new String[] { IN1, IN2 },
					new String[] { OUT });
			datalink(topMaster, currentProcessor, currentPort, procName, IN1);
			datalink(topMaster, linkingDataflowName, portName, procName, IN2);
			currentProcessor = procName;
			currentPort = OUT;
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
			String portName = text(port, PORT_NAME);
			SemanticAnnotations p = getAnnotations(port);
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

	private boolean getSubjectTypeFromAnnotation(String name, SemanticAnnotations sa,
			Holder<String> subject, Holder<String> type) {
		try {
			subject.value = sa.getProperty(TAVERNA_SUBJECT_PROPERTY);
			try {
				if (subject.value != null)
					subject.value = new URI(subject.value).getFragment().trim();
				if (subject.value == null || subject.value.isEmpty())
					subject.value = defaultSubject;
			} catch (URISyntaxException | NullPointerException e) {
				if (subject.value == null)
					subject.value = defaultSubject;
			}
			type.value = sa.getProperty(TAVERNA_TYPE_PROPERTY);
			if (type.value == null) {
				String provided = sa.getProperty(SCAPE_PROVIDES_PROPERTY);
				if (provided != null && !provided.equals(SCAPE_TARGET_OBJECT))
					type.value = provided;
			}
			if (!subject.value.isEmpty() && type.value != null
					&& !type.value.isEmpty()) {
				log.info(format("port %s has subject %s and type %s",
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

	private static class Pair {
		String type, name;

		Pair(Holder<String> type, String name) {
			this.type = type.value;
			this.name = name;
		}
	}

	@Nonnull
	private Set<String> connectInnerOutputsToTop(@Nonnull Element topMaster,
			@Nonnull Element innerMaster, @Nonnull Element outerMaster,
			@Nonnull Set<String> createdOut) throws Exception {
		for (Element out: select(innerMaster, OUTPUT_PORT_LIST + PORTS)) {
			String name = text(out, PORT_NAME);
			SemanticAnnotations ann = getAnnotations(out);
			if (SCAPE_TARGET_OBJECT.equals(ann.getProperty(SCAPE_PROVIDES_PROPERTY))) {
				datalink(outerMaster, innerProcessorName, name, null, workflowOutputName);
				break;
			}
		}
		Map<String, List<Pair>> types = new HashMap<>();
		Set<String> topPortSet = new HashSet<>();
		Element outPorts = get(outerMaster, OUTPUT_PORT_LIST);
		Element topProcessor = get(topMaster, NAMED_PROCESSOR,
				linkingDataflowName);
		Element topPorts = get(topProcessor, OUTPUT_PORT_LIST);

		Holder<String> subjectHolder = new Holder<>();
		Holder<String> typeHolder = new Holder<>();
		for (String out : createdOut) {
			if (!getSubjectType(out,
					get(innerMaster, OUTPUT_PORT_LIST + NAMED_PORT, out),
					subjectHolder, typeHolder))
				continue;
			final String subject = subjectHolder.value;

			if (!types.containsKey(subject)) {
				types.put(subject, new ArrayList<Pair>());
				String mp = "measures_" + subject;
				topPortSet.add(mp);
				Element outp = branch(outPorts, "port");
				leaf(outp, "name", mp);
				branch(outp, "annotations");
				if (topPorts != null)
					port(topPorts, mp, 0, 0);
				mapOutput(topProcessor, mp);
			}
			types.get(subject).add(new Pair(typeHolder, out));
		}
		Element links = get(outerMaster, DATALINKS);
		for (String subject : types.keySet())
			createMetricDocument(outerMaster, links, subject, types.get(subject));
		return topPortSet;
	}

	private String createMetricDocument(Element dataflow, Element links,
			String subject, List<Pair> types) throws Exception {
		String dbName = "ScapeMetricDocumentBuilder_" + subject;
		String typesName = "MetricTypes_" + subject;
		String subjectName = "MetricSubject_" + subject;

		StringBuilder sb = new StringBuilder();
		String sep = "";
		for (Pair type : types) {
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
		makeConstant(dataflow, subjectName, subject);
		makeConstant(dataflow, typesName, sb.toString());

		// The port names below
		final String VALUES = "values";
		final String SUBJECT = "subject";
		final String TYPES = "types";
		final String OUT = "metricDocument";

		Element docBuilder = makeComponent(dataflow, dbName, repository,
				utilityFamily, builderName, builderVersion, new String[] {
						TYPES, VALUES, SUBJECT }, new String[] { OUT });
		get(docBuilder, INPUT_PORT_LIST + NAMED_PORT + "/" + PORT_DEPTH,
				VALUES).setTextContent("1");
		Element istrat = get(docBuilder,
				"t:iterationStrategyStack/t:iteration/t:strategy");
		Element cross = branch(istrat, "cross");
		Element values = get(istrat, "t:dot/t:port[@name = \"%s\"]", VALUES);
		if (values != null) {
			values.setAttribute("depth", "1");
			cross.appendChild(values);
		}
		cross.appendChild(get(istrat, "t:dot"));

		String out = "measures_" + subject; 
		datalink(dataflow, subjectName, "value", dbName, SUBJECT);
		datalink(dataflow, typesName, "value", dbName, TYPES);
		datalink(dataflow, dbName, OUT, null, out);
		return out;
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
		for (Element in: select(innerMaster, INPUT_PORT_LIST + PORTS)) {
			String name = text(in, PORT_NAME);
			SemanticAnnotations ann = getAnnotations(in);
			if (SCAPE_SOURCE_OBJECT.equals(ann.getProperty(SCAPE_ACCEPTS_PROPERTY))) {
				datalink(outerMaster, null, workflowInputName, innerProcessorName, name);
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

	@Override
	protected void postProcess(@Nonnull Element documentElement)
			throws XPathException {
		for (Element config : select(
				documentElement,
				"//t:processors/t:processor/t:activities/t:activity[t:class='%s']/t:configBean/%s",
				BEANSHELL_PKG + "BeanshellActivity", BEANSHELL_PKG
						+ "BeanshellActivityConfigurationBean"))
			if (text(config, "script").contains(
					"org.taverna.server.master.scape.beanshells."))
				addBeanshellArtifactDependency(config, "uk.org.taverna.server",
						"scape-operations", "123.456");
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
