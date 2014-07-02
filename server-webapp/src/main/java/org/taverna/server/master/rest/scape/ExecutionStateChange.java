package org.taverna.server.master.rest.scape;

import static org.taverna.server.master.rest.scape.Namespaces.SCAPE_MODEL;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.datatype.XMLGregorianCalendar;

import eu.scape_project.model.plan.PlanExecutionState;

/**
 * Rich message sent to update the Preservation Plan execution state.
 * @author Donal Fellows
 * @see PlanExecutionState
 */
@XmlRootElement(name = "plan-execution-state", namespace = SCAPE_MODEL)
public class ExecutionStateChange {
	@XmlAttribute(required = true, name = "timeStamp", namespace = SCAPE_MODEL)
	public XMLGregorianCalendar timestamp;
	@XmlAttribute(required = true, namespace = SCAPE_MODEL)
	public State state;
	@XmlAttribute(name = "output-location")
	public String output;
	@XmlAttribute(name = "provenance-bundle-location")
	public String provenance;
	@XmlValue
	public String contents;

	public ExecutionStateChange() {
	}

	public ExecutionStateChange(State state, String contents) {
		this.state = state;
		this.contents = contents;
	}

	@XmlType
	@XmlEnum
	public static enum State {
		@XmlEnumValue("EXECUTION_IN_PROGRESS") InProgress,
		@XmlEnumValue("EXECUTION_SUCCESS") Success,
		@XmlEnumValue("EXECUTION_FAIL") Fail
	}
}
