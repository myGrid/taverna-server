package org.taverna.server.master.rest.scape;

import static org.taverna.server.master.rest.scape.Namespaces.PLATO;

import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.w3c.dom.Element;

@XmlRootElement(name = "preservationActionPlan", namespace = PLATO)
@XmlType(name = "PreservationActionPlan", namespace = PLATO)
public class PreservationActionPlan {
	@XmlAttribute(name = "schemaVersion", required = true)
	public String version;
	@XmlElement(required = true)
	public Collection collection;
	@XmlElement(name = "object")
	@XmlElementWrapper(name = "objects", required = true)
	public List<DigitalObject> object;
	@XmlElement(required = true)
	public ExecutablePlan executablePlan;
	@XmlElement
	public QualityLevelDescription qualityLevelDescription;

	@XmlType(name = "Collection", namespace=PLATO)
	public static class Collection {
		@XmlAttribute(required=true)
		public String uid;
		@XmlAttribute
		public String name;
	}

	@XmlType(name="Object",namespace=PLATO)
	public static class DigitalObject {
		@XmlAttribute(required=true)
		public String uid;
	}

	@XmlType(name="ExecutablePlan",namespace=PLATO)
	public static class ExecutablePlan {
		@XmlAttribute
		public ExecutablePlanType type;
		@XmlAttribute
		public String id;
		@XmlAnyElement(lax = false)
		public Element workflowDocument;

		@XmlEnum 
		public enum ExecutablePlanType {
			@XmlEnumValue("t2flow") Taverna2;
		}
	}

	@XmlType(name="QualityLevelDescription",namespace=PLATO)
	public static class QualityLevelDescription {
		@XmlAnyElement(lax = false)
		public Element schematronDocument;
	}
}
