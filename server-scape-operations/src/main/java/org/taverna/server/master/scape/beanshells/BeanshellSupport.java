package org.taverna.server.master.scape.beanshells;


/**
 * Instances of this interface are used to help write the beanshell scripts
 * inside the outer workflows.
 * 
 * @author Donal Fellows
 * @deprecated Do not use directly
 */
@Deprecated
public interface BeanshellSupport {
	public void shell() throws Exception;
}
