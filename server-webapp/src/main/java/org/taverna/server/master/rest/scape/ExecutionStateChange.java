package org.taverna.server.master.rest.scape;

import static org.taverna.server.master.rest.scape.Namespaces.SCAPE_MODEL;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlRootElement(name = "plan-execution-state", namespace = SCAPE_MODEL)
public class ExecutionStateChange {
	@XmlAttribute(required = true)
	public XMLGregorianCalendar timestamp;
	@XmlAttribute(required = true)
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

	public static enum State {
		InProgress("EXECUTION_IN_PROGRESS"), Success("EXECUTION_SUCCESS"), Fail(
				"EXECUTION_FAIL");
		private final String name;

		private State(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
