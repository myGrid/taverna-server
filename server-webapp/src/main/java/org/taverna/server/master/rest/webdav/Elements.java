/*
 * Copyright (C) 2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.rest.webdav;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

import org.w3c.dom.Element;

/**
 * Holder for various XML elements used in WebDAV.
 * 
 * @author Donal Fellows
 */
public class Elements {

	/**
	 * Holder for various requests defined in the WebDAV specification.
	 * 
	 * @see Property
	 * @author Donal Fellows
	 */
	public static class Requests {
		@XmlRootElement(name = "lockinfo")
		@XmlType(name = "LockInfo")
		public static class LockInfo {
			@XmlElement(name = "lockscope", required = true)
			public LockScope scope;
			@XmlElement(name = "locktype", required = true)
			public LockType type;
			@XmlElement(name = "owner", required = false)
			public Owner owner;
		}

		@XmlRootElement(name = "propertyupdate")
		@XmlType(name = "PropertyUpdate")
		public static class PropertyUpdate {
			@XmlElements({ @XmlElement(name = "remove", type = Remove.class),
					@XmlElement(name = "set", type = Set.class) })
			public List<Operation> updates;

			@XmlType(name = "PropertyOperation")
			public static abstract class Operation {
				@XmlElement(name = "prop")
				public Property value;
			}

			@XmlType(name = "")
			public static class Remove extends Operation {
			}

			@XmlType(name = "")
			public static class Set extends Operation {
			}
		}

		@XmlRootElement(name = "propfind")
		@XmlType(name = "PropFind")
		@XmlAccessorOrder
		public static class PropertyFind {
			@XmlElements({
					@XmlElement(name = "propname", type = PropertyName.class),
					@XmlElement(name = "allprop", type = AllProperties.class),
					@XmlElement(name = "include", type = Include.class),
					@XmlElement(name = "prop", type = Property.class) })
			public List<Object> content;

			@XmlType(name = "")
			public static class PropertyName {
				// Nothing
			}

			@XmlType(name = "")
			public static class AllProperties {
				// Nothing
			}

			@XmlType(name = "")
			public static class Include {
				@XmlAnyElement(lax = true)
				List<Element> content;
			}
		}
	}

	/**
	 * Holder for various responses defined in the WebDAV specification.
	 * 
	 * @see Property
	 * @author Donal Fellows
	 */
	public static class Responses {
		@XmlRootElement(name = "multistatus")
		@XmlType(name = "MultiStatus")
		public static class MultiStatus {
			@XmlElement(name = "response")
			public List<Response> responses;
			@XmlElement(name = "responsedescription")
			public String description;
		}

		@XmlRootElement(name = "error")
		@XmlType(name = "Error")
		public static class Error {
			@XmlAnyElement(lax = true)
			List<Element> content;

			@XmlRootElement(name = "lock-token-matches-request-uri")
			public static class TokenMatchesURI {
				// Nothing
			}

			@XmlRootElement(name = "lock-token-submitted")
			public static class TokenSubmitted {
				@XmlElement(name = "href", required = true)
				public List<HRef> references;
			}

			@XmlRootElement(name = "no-conflicting-lock")
			public static class NoConflict {
				@XmlElement(name = "href", required = false)
				public List<HRef> references;
			}

			@XmlRootElement(name = "no-external-entities")
			public static class NoEntities {
				// Nothing
			}

			@XmlRootElement(name = "propfind-finite-depth")
			public static class FiniteDepth {
				// Nothing
			}

			@XmlRootElement(name = "cannot-modify-protected-property")
			public static class Protected {
				// Nothing
			}
		}

		@XmlRootElement(name = "response")
		@XmlType(name = "Response")
		public static class Response {
			@XmlElement(name = "href", required = true)
			public HRef ref;
			@XmlElements({ @XmlElement(name = "href", type = HRef.class),
					@XmlElement(name = "status", type = String.class),
					@XmlElement(name = "propstat", type = PropertyStatus.class) })
			public List<Object> info;
			@XmlElement(name = "error", required = false)
			public Error error;
			@XmlElement(name = "responsedescription", required = false)
			public String description;
			@XmlElement(name = "location", required = false)
			public Location location;

			@XmlType(name = "")
			public static class PropertyStatus {
				@XmlElement(name = "prop", required = true)
				public Property property;
				@XmlElement(name = "status", required = true)
				public String status;
				@XmlElement(name = "error", required = false)
				public Error error;
				@XmlElement(name = "responsedescription", required = false)
				public String description;
			}

			@XmlType(name = "")
			public static class Location {
				@XmlElement(name = "href")
				public HRef value;
			}
		}
	}

	@XmlType
	public static class HRef {
		@XmlValue
		@XmlSchemaType(name = "anyURI")
		public URI value;
	}

	@XmlType(name = "LockScope")
	public static class LockScope {
		@XmlElements({
				@XmlElement(name = "exclusive", type = Exclusive.class, required = true),
				@XmlElement(name = "shared", type = Shared.class, required = true) })
		public Scope value;

		@XmlType
		public static class Scope {
			// Nothing
		}

		@XmlType(name = "")
		public static class Exclusive extends Scope {
			// Nothing
		}

		@XmlType(name = "")
		public static class Shared extends Scope {
			// Nothing
		}
	}

	@XmlType(name = "LockType")
	public static class LockType {
		@XmlElements({ @XmlElement(name = "write", type = Write.class, required = true) })
		public Type type;

		@XmlType(name = "LockTypeType")
		public static class Type {
			// Nothing
		}

		@XmlType(name = "")
		public static class Write extends Type {
			// Nothing
		}
	}

	@XmlType(name = "Owner")
	public static class Owner {
		@XmlAnyElement
		@XmlMixed
		List<?> content;
	}

	@XmlRootElement(name = "prop")
	@XmlType(name = "Property")
	public static class Property {
		@XmlAnyElement(lax = true)
		List<Element> content;
	}

	/**
	 * Holder for various properties defined in the WebDAV specification.
	 * 
	 * @author Donal Fellows
	 */
	public static class Properties {
		@XmlRootElement(name = "creationdate")
		public static class CreationDate {
			@XmlValue
			public String value;// TODO (rfc1123, Section 3.3.1 of [RFC2616])
		}

		@XmlRootElement(name = "displayname")
		public static class DisplayName {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getcontentlanguage")
		public static class ContentLanguage {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getcontentlength")
		public static class ContentLength {
			@XmlValue
			public long value;
		}

		@XmlRootElement(name = "getcontenttype")
		public static class ContentType {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getetag")
		public static class ETag {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getlastmodified")
		public static class LastModified {
			@XmlValue
			public String value;// TODO (rfc1123, Section 3.3.1 of [RFC2616])
		}

		@XmlRootElement(name = "lockdiscovery")
		public static class LockDiscovery {
			@XmlElement(name = "activelock")
			public List<Active> content;

			@XmlType(name = "ActiveLock")
			public static class Active {
				@XmlElement(name = "lockscope", required = true)
				public LockScope scope;
				@XmlElement(name = "locktype", required = true)
				public LockType type;
				@XmlElement(name = "depth", required = true)
				public Depth depth;
				@XmlElement(name = "owner", required = false)
				public Owner owner;
				@XmlElement(name = "timeout", required = false)
				public String timeout;
				@XmlElement(name = "locktoken", required = false)
				public HRef token;
				@XmlElement(name = "lockroot", required = true)
				public HRef root;
			}
		}

		@XmlRootElement(name = "resourcetype")
		public static class ResourceType {
			@XmlAnyElement(lax = true)
			public List<Object> content;

			@XmlRootElement(name = "collection")
			@XmlType(name = "")
			public static class Collection {
				// Nothing
			}
		}

		@XmlRootElement(name = "supportedlock")
		public static class SupportedLock {
			@XmlElement(name = "lockentry")
			public List<Entry> content;

			@XmlType(name = "LockEntry")
			public static class Entry {
				@XmlElement(name = "lockscope", required = true)
				public LockScope scope;
				@XmlElement(name = "locktype", required = true)
				public LockType type;
			}
		}
	}
}
