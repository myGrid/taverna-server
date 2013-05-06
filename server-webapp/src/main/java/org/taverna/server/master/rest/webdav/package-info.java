/*
 * Copyright (C) 2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
/**
 * Various classes used to support WebDAV within the REST interface.
 * @author Donal Fellows
 */
@XmlSchema(namespace = WEBDAV, elementFormDefault = QUALIFIED, xmlns = {
		@XmlNs(prefix = "xlink", namespaceURI = XLINK),
		@XmlNs(prefix = "ts", namespaceURI = SERVER),
		@XmlNs(prefix = "ts-rest", namespaceURI = SERVER_REST),
		@XmlNs(prefix = "ts-soap", namespaceURI = SERVER_SOAP),
		@XmlNs(prefix = "port", namespaceURI = DATA),
		@XmlNs(prefix = "feed", namespaceURI = FEED),
		@XmlNs(prefix = "admin", namespaceURI = ADMIN),
		@XmlNs(prefix = "D", namespaceURI = WEBDAV) })
package org.taverna.server.master.rest.webdav;

import static javax.xml.bind.annotation.XmlNsForm.QUALIFIED;
import static org.taverna.server.master.common.Namespaces.ADMIN;
import static org.taverna.server.master.common.Namespaces.FEED;
import static org.taverna.server.master.common.Namespaces.SERVER;
import static org.taverna.server.master.common.Namespaces.SERVER_REST;
import static org.taverna.server.master.common.Namespaces.SERVER_SOAP;
import static org.taverna.server.master.common.Namespaces.WEBDAV;
import static org.taverna.server.master.common.Namespaces.XLINK;
import static org.taverna.server.port_description.Namespaces.DATA;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;

