/*
 * Copyright (C) 2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.rest.webdav;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

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
		@XmlType(name = "")
		public static class LockInfo {
			@XmlElement(name = "lockscope", required = true)
			public LockScope scope;
			@XmlElement(name = "locktype", required = true)
			public LockType type;
			@XmlElement(name = "owner", required = false)
			public Owner owner;
		}

		@XmlRootElement(name = "propertyupdate")
		@XmlType(name = "")
		public static class PropertyUpdate {
			@XmlElementRefs({ @XmlElementRef(type = Remove.class),
					@XmlElementRef(type = Set.class) })
			public List<Object> updates;

			@XmlRootElement(name = "remove")
			@XmlType(name = "")
			public static class Remove {
				@XmlElement(name = "prop")
				public Property value;
			}

			@XmlRootElement(name = "set")
			@XmlType(name = "")
			public static class Set {
				@XmlElement(name = "prop")
				public Property value;
			}
		}

		@XmlRootElement(name = "propfind")
		@XmlType(name = "")
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
				List<Object> content;
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
		@XmlType(name = "")
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
			@XmlElementRefs({ @XmlElementRef(type = Error.FiniteDepth.class),
					@XmlElementRef(type = Error.NoConflict.class),
					@XmlElementRef(type = Error.NoEntities.class),
					@XmlElementRef(type = Error.Protected.class),
					@XmlElementRef(type = Error.TokenMatchesURI.class),
					@XmlElementRef(type = Error.TokenSubmitted.class), })
			List<Object> content;

			@XmlRootElement(name = "lock-token-matches-request-uri")
			@XmlType(name = "")
			public static class TokenMatchesURI {
				// Nothing
			}

			@XmlRootElement(name = "lock-token-submitted")
			@XmlType(name = "")
			public static class TokenSubmitted {
				@XmlElement(name = "href", required = true)
				public List<HRef> references;
			}

			@XmlRootElement(name = "no-conflicting-lock")
			@XmlType(name = "")
			public static class NoConflict {
				@XmlElement(name = "href", required = false)
				public List<HRef> references;
			}

			@XmlRootElement(name = "no-external-entities")
			@XmlType(name = "")
			public static class NoEntities {
				// Nothing
			}

			@XmlRootElement(name = "propfind-finite-depth")
			@XmlType(name = "")
			public static class FiniteDepth {
				// Nothing
			}

			@XmlRootElement(name = "cannot-modify-protected-property")
			@XmlType(name = "")
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

	@XmlType(name = "HRef")
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

		@XmlType(name = "LockScopeType")
		public static abstract class Scope {
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
		public static abstract class Type {
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
		List<Object> content;
	}

	/**
	 * Note that this is potentially found as both a request and response
	 * message.
	 * 
	 * @author Donal Fellows
	 */
	@XmlRootElement(name = "prop")
	@XmlType(name = "Property")
	public static class Property {
		@XmlAnyElement(lax = true)
		@XmlElementRefs({
				@XmlElementRef(type = Properties.ContentLanguage.class),
				@XmlElementRef(type = Properties.ContentLength.class),
				@XmlElementRef(type = Properties.ContentType.class),
				@XmlElementRef(type = Properties.CreationDate.class),
				@XmlElementRef(type = Properties.DisplayName.class),
				@XmlElementRef(type = Properties.ETag.class),
				@XmlElementRef(type = Properties.LastModified.class),
				@XmlElementRef(type = Properties.LockDiscovery.class),
				@XmlElementRef(type = Properties.ResourceType.class),
				@XmlElementRef(type = Properties.SupportedLock.class) })
		List<Object> content;
	}

	/**
	 * Holder for various properties defined in the WebDAV specification.
	 * 
	 * @author Donal Fellows
	 */
	public static class Properties {
		@XmlRootElement(name = "creationdate")
		@XmlType(name = "")
		public static class CreationDate {
			@XmlValue
			public String value;// TODO (rfc1123, Section 3.3.1 of [RFC2616])
		}

		@XmlRootElement(name = "displayname")
		@XmlType(name = "")
		public static class DisplayName {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getcontentlanguage")
		@XmlType(name = "")
		public static class ContentLanguage {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getcontentlength")
		@XmlType(name = "")
		public static class ContentLength {
			@XmlValue
			public long value;
		}

		@XmlRootElement(name = "getcontenttype")
		@XmlType(name = "")
		public static class ContentType {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getetag")
		@XmlType(name = "")
		public static class ETag {
			@XmlValue
			public String value;
		}

		@XmlRootElement(name = "getlastmodified")
		@XmlType(name = "")
		public static class LastModified {
			@XmlValue
			public String value;// TODO (rfc1123, Section 3.3.1 of [RFC2616])
		}

		@XmlRootElement(name = "lockdiscovery")
		@XmlType(name = "")
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
		@XmlType(name = "")
		public static class ResourceType {
			@XmlAnyElement(lax = true)
			@XmlElementRef(type = ResourceType.Collection.class)
			public List<Object> content;

			@XmlRootElement(name = "collection")
			@XmlType(name = "")
			public static class Collection {
				// Nothing
			}
		}

		@XmlRootElement(name = "supportedlock")
		@XmlType(name = "")
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
