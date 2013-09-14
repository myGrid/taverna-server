package org.taverna.server.master.api;

import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link ListenersREST}.
 * 
 * @author Donal Fellows
 */
public interface ListenersBean extends SupportAware {
	@NonNull TavernaServerListenersREST connect(@NonNull TavernaRun run);
}