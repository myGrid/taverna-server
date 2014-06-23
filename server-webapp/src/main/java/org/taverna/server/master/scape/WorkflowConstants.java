package org.taverna.server.master.scape;

interface WorkflowConstants {
	String TAVERNA_SPLICER_URL = "http://ns.taverna.org.uk/taverna-server/splicing";
	String TAVERNA_SUBJECT_PROPERTY = TAVERNA_SPLICER_URL
			+ "#OutputPortSubject";
	String TAVERNA_TYPE_PROPERTY = TAVERNA_SPLICER_URL + "#OutputPortType";
	String SCAPE_URL = "http://purl.org/DP/components";
	String SCAPE_ACCEPTS_PROPERTY = SCAPE_URL + "#accepts";
	String SCAPE_PROVIDES_PROPERTY = SCAPE_URL + "#provides";
	String SCAPE_SOURCE_OBJECT = SCAPE_URL + "#SourceObject";
	String SCAPE_TARGET_OBJECT = SCAPE_URL + "#TargetObject";
}
