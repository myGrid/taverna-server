package org.taverna.server.master.rest.scape;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlRootElement(name = "execution-state", namespace = "")
public class ExecutionStateChange {
	@XmlAttribute
	public XMLGregorianCalendar timestamp;
	@XmlAttribute
	public State state;
	@XmlValue
	public String contents;

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
