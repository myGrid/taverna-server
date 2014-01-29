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
import static org.taverna.server.master.scape.XPaths.PORT_DEPTH;
import static org.taverna.server.master.scape.XPaths.REQUIRE_NESTED;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Holder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.common.Workflow;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;

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
		One2OneNoSchema("1to1"), One2OneSchema("1to1_schema");
		@NonNull
		private final String key;

		private Model(@NonNull String key) {
			this.key = key;
		}

		@NonNull
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

	public ScapeSplicingEngine() throws ParserConfigurationException {
		super(log);
	}

	public void setComponentRepositoryAddress(String address)
			throws MalformedURLException {
		// Check if sane URI and URL!
		URI.create(address).toURL();
		repository = address;
	}

	public void setUtilityComponentFamily(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException("family name must be non-empty");
		utilityFamily = name;
	}

	public void setBuilderComponentName(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException(
					"component name must be non-empty");
		builderName = name;
	}

	public void setBuilderComponentVersion(int version) {
		if (version < 1)
			throw new IllegalArgumentException("version must be positive");
		builderVersion = Integer.toString(version);
	}

	public void setJoinerComponentName(String name) {
		if (name.isEmpty())
			throw new IllegalArgumentException(
					"component name must be non-empty");
		joinerName = name;
	}

	public void setJoinerComponentVersion(int version) {
		if (version < 1)
			throw new IllegalArgumentException("version must be positive");
		joinerVersion = Integer.toString(version);
	}

	@Required
	public void setDummyProcessorName(String name) {
		dummyProcessorName = name;
	}

	@NonNull
	public Workflow constructWorkflow(@NonNull Element executablePlan,
			@NonNull Model model) throws Exception {
		return constructWorkflow(executablePlan, model.getKey());
	}

	@NonNull
	synchronized Element getWrapperInstance(@NonNull Model model)
			throws IOException, ParserConfigurationException, SAXException {
		return getWrapperInstance(model.getKey());
	}

	@Override
	protected void connectInnerToOuter(@NonNull Element topDataflow,
			@NonNull Element linkingDataflow,
			@NonNull Element insertedDataflow,
			@NonNull Set<String> createdInputNames,
			@NonNull Set<String> createdOutputNames) throws Exception {
		connectInnerInputsToTop(topDataflow, linkingDataflow, insertedDataflow,
				createdInputNames);
		@NonNull
		Set<String> linkingOutputNames = connectInnerOutputsToTop(topDataflow,
				insertedDataflow, linkingDataflow, createdOutputNames);
		concatenateDocuments(topDataflow, linkingOutputNames);
	}

	private void concatenateDocuments(@NonNull Element topMaster,
			@NonNull Set<String> subjectPorts) throws Exception {
		int counter = 0;
		String sourceProcessor = null, currentPort = null;
		for (String portName : subjectPorts) {
			if (portName == null)
				continue;
			if (currentPort == null) {
				sourceProcessor = linkingDataflowName;
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
			datalink(topMaster, linkingDataflowName, portName, procName,
					"metricDocument2");
			sourceProcessor = procName;
			currentPort = "combinedMetricDocument";
		}

		for (Element e : select(topMaster, NAMED_PROCESSOR, dummyProcessorName))
			e.getParentNode().removeChild(e);

		if (currentPort != null)
			for (Element e : select(topMaster, DATALINK_FROM_PROCESSOR,
					dummyProcessorName)) {
				datalink(topMaster, sourceProcessor, currentPort,
						text(e, "t:sink/t:processor"), text(e, "t:sink/t:port"));
				e.getParentNode().removeChild(e);
			}
	}

	protected boolean getSubjectType(String name, Element port,
			Holder<String> subject, Holder<String> type) {
		Matcher m = PORT_INFO_EXTRACT.matcher(name);
		if (!m.matches())
			return false;
		subject.value = m.group(1);
		type.value = m.group(2);
		return true;
	}

	@NonNull
	private Set<String> connectInnerOutputsToTop(@NonNull Element topMaster,
			@NonNull Element innerMaster, @NonNull Element outerMaster,
			@NonNull Set<String> createdOut) throws Exception {
		Map<String, List<String>> types = new HashMap<String, List<String>>();
		Set<String> topPortSet = new HashSet<String>();
		Element outPorts = get(outerMaster, OUTPUT_PORT_LIST);
		Element topProcessor = get(topMaster, NAMED_PROCESSOR,
				linkingDataflowName);
		Element topPorts = get(topProcessor, OUTPUT_PORT_LIST);
		for (String out : createdOut) {
			Holder<String> subject = new Holder<String>();
			Holder<String> type = new Holder<String>();
			if (!getSubjectType(out,
					get(innerMaster, OUTPUT_PORT_LIST + NAMED_PORT, out),
					subject, type))
				continue;
			if (!types.containsKey(subject.value)) {
				types.put(subject.value, new ArrayList<String>());
				String mp = "measures_" + subject.value;
				topPortSet.add(mp);
				Element outp = branch(outPorts, "port");
				leaf(outp, "name", mp);
				branch(outp, "annotations");
				if (topPorts != null)
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
				leaf(source, "processor", innerProcessorName);
				leaf(source, "port", "measure_" + subject + "_" + type);
				sb.append(sep).append(type);
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

	private void connectInnerInputsToTop(@NonNull Element topMaster,
			@NonNull Element outerMaster, @NonNull Element innerMaster,
			@NonNull Set<String> createdIn) throws Exception {
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
		@NonNull
		Element executablePlan = e.parse(new InputSource(from));

		System.out.println("setup ok");

		Workflow w = e.constructWorkflow(executablePlan, Model.One2OneNoSchema);

		System.out.println("transform ok");

		IOUtils.write(e.write(w.getWorkflowRoot()), new FileOutputStream(to),
				"UTF-8");
		System.out.println("wrote results to " + to);
	}
}
