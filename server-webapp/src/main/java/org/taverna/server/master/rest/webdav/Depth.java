/*
 * Copyright (C) 2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.rest.webdav;

import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

/**
 * The depth to which to apply operations on collections.
 * 
 * @author Donal Fellows
 */
@XmlType(name = "Depth")
public enum Depth {
	/** Just the named entity. */
	@XmlEnumValue("0")
	Zero, /** The named entity and its immediate children. */
	@XmlEnumValue("1")
	One, /** The named entity and all its desendants. */
	@XmlEnumValue("infinity")
	Infinity;
}
