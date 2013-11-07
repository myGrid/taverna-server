/**
 * The public view of the SCAPE project Plan Execution interface.
 * @author Donal Fellows
 */
@XmlSchema(namespace = SCAPE, elementFormDefault = QUALIFIED, attributeFormDefault = QUALIFIED, xmlns = {
		@XmlNs(prefix = "xlink", namespaceURI = XLINK),
		@XmlNs(prefix = "ts", namespaceURI = SERVER),
		@XmlNs(prefix = "ts-rest", namespaceURI = SERVER_REST),
		@XmlNs(prefix = "ts-soap", namespaceURI = SERVER_SOAP),
		@XmlNs(prefix = "port", namespaceURI = DATA),
		@XmlNs(prefix = "feed", namespaceURI = FEED),
		@XmlNs(prefix = "admin", namespaceURI = ADMIN),
		@XmlNs(prefix = "plato", namespaceURI = PLATO),
		@XmlNs(prefix = "scape", namespaceURI = SCAPE) })
package org.taverna.server.master.rest.scape;

import static javax.xml.bind.annotation.XmlNsForm.QUALIFIED;
import static org.taverna.server.master.common.Namespaces.ADMIN;
import static org.taverna.server.master.common.Namespaces.FEED;
import static org.taverna.server.master.common.Namespaces.SERVER;
import static org.taverna.server.master.common.Namespaces.SERVER_REST;
import static org.taverna.server.master.common.Namespaces.SERVER_SOAP;
import static org.taverna.server.master.common.Namespaces.XLINK;
import static org.taverna.server.master.rest.scape.Namespaces.PLATO;
import static org.taverna.server.master.rest.scape.Namespaces.SCAPE;
import static org.taverna.server.port_description.Namespaces.DATA;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;

