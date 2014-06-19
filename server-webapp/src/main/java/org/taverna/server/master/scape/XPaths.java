package org.taverna.server.master.scape;

public interface XPaths {
	String DATALINK_FROM_PROCESSOR = "t:datalinks/t:datalink[t:source/t:processor=\"%s\"]";
	String NAMED_PORT = "/t:port[t:name = \"%s\"]";
	String OUTPUT_PORT_LIST = "t:outputPorts";
	String INPUT_PORT_LIST = "t:inputPorts";
	String ITERATION_STRATEGY = "t:iterationStrategyStack/t:iteration/t:strategy";
	String NAMED_PROCESSOR = "t:processors/t:processor[t:name=\"%s\"]";
	String REQUIRE_NESTED = "[t:activities/t:activity/t:raven/t:artifact=\"dataflow-activity\"]";
	String PORT_DEPTH = "t:depth";
	String PORT_NAME = "t:name";
	String PORT = "t:port";
	String PORTS = "/t:port";
	String DATALINKS = "t:datalinks";
}
