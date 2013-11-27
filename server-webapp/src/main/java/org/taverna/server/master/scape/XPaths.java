package org.taverna.server.master.scape;

public interface XPaths {
	public static final String DATALINK_FROM_PROCESSOR = "t:datalinks/t:datalink[t:source/t:processor=\"%s\"]";
	public static final String NAMED_PORT = "/t:port[t:name = \"%s\"]";
	public static final String OUTPUT_PORT_LIST = "t:outputPorts";
	public static final String INPUT_PORT_LIST = "t:inputPorts";
	public static final String ITERATION_STRATEGY = "t:iterationStrategyStack/t:iteration/t:strategy";
	public static final String NAMED_PROCESSOR = "t:processors/t:processor[t:name=\"%s\"]";
	public static final String REQUIRE_NESTED = "[t:activities/t:activity/t:raven/t:artifact=\"dataflow-activity\"]";
	public static final String PORT_DEPTH = "t:depth";
	public static final String PORT_NAME = "t:name";
	public static final String PORT = "t:port";
}
