package org.taverna.server.master.rest.webdav;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.HttpMethod;

/**
 * Indicates that the annotated method responds to WebDAV MKCOL requests
 * @see HttpMethod
 * @see WebDAVMethod
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod(WebDAVMethod.MKCOL)
public @interface MKCOL {

}
