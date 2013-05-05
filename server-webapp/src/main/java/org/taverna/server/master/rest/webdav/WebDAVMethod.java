package org.taverna.server.master.rest.webdav;

import static org.taverna.server.master.rest.webdav.WebDAVMethodSupport.RO_HTTP_METHODS;
import static org.taverna.server.master.rest.webdav.WebDAVMethodSupport.RW_HTTP_METHODS;
import static org.taverna.server.master.rest.webdav.WebDAVMethodSupport.c;

import javax.ws.rs.HttpMethod;

/**
 * The additional HTTP methods supported in WebDAV.
 * 
 * @author Donal Fellows
 * @see <a href="http://tools.ietf.org/html/rfc4918">RFC-4918</a>
 */
public interface WebDAVMethod {
	public static final String COPY = "COPY";
	public static final String LOCK = "LOCK";
	public static final String MKCOL = "MKCOL";
	public static final String MOVE = "MOVE";
	public static final String PROPFIND = "PROPFIND";
	public static final String PROPPATCH = "PROPPATCH";
	public static final String UNLOCK = "UNLOCK";
	/**
	 * The full list of methods supported. Except for the locking; locking is
	 * stupid.
	 */
	public static final String FULL_LIST = RO_HTTP_METHODS + c
			+ RW_HTTP_METHODS + c + COPY + c + MOVE + c + MKCOL + c + PROPFIND
			+ c + PROPPATCH;
	/** The set of methods supported when not granted write permissions. */
	public static final String READ_LIST = RO_HTTP_METHODS + c + PROPFIND;
}

/**
 * A holder for constants that shouldn't be widely exposed.
 * 
 * @author Donal Fellows
 */
interface WebDAVMethodSupport {
	static final String c = ", ";
	static final String RO_HTTP_METHODS = HttpMethod.OPTIONS + c
			+ HttpMethod.GET + c + HttpMethod.HEAD;
	static final String RW_HTTP_METHODS = HttpMethod.POST + c + HttpMethod.PUT
			+ c + HttpMethod.DELETE;
}