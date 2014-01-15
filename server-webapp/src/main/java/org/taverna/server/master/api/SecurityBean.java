package org.taverna.server.master.api;

import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.TavernaSecurityContext;
import org.taverna.server.master.rest.TavernaServerSecurityREST;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link RunSecurityREST}.
 * 
 * @author Donal Fellows
 */
public interface SecurityBean extends SupportAware {
	@NonNull
	TavernaServerSecurityREST connect(@NonNull TavernaSecurityContext context,
			@NonNull TavernaRun run);
}