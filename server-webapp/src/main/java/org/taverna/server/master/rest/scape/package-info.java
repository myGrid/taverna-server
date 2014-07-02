/**
 * The public view of the SCAPE project Plan Execution interface.
 * @author Donal Fellows
 */
@XmlSchema(namespace = SCAPE, elementFormDefault = QUALIFIED, attributeFormDefault = QUALIFIED, xmlns = {
		@XmlNs(prefix = "xlink", namespaceURI = XLINK),
		@XmlNs(prefix = "tavserv", namespaceURI = SERVER),
		@XmlNs(prefix = "plato", namespaceURI = PLATO),
		@XmlNs(prefix = "exec", namespaceURI = SCAPE),
		@XmlNs(prefix = "scape", namespaceURI = SCAPE_MODEL)})
package org.taverna.server.master.rest.scape;

import static javax.xml.bind.annotation.XmlNsForm.QUALIFIED;
import static org.taverna.server.master.common.Namespaces.SERVER;
import static org.taverna.server.master.common.Namespaces.XLINK;
import static org.taverna.server.master.rest.scape.Namespaces.PLATO;
import static org.taverna.server.master.rest.scape.Namespaces.SCAPE;
import static org.taverna.server.master.rest.scape.Namespaces.SCAPE_MODEL;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;

