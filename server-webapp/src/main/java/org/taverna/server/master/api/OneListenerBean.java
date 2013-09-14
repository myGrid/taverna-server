package org.taverna.server.master.api;

import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST.TavernaServerListenerREST;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link InputREST}.
 * 
 * @author Donal Fellows
 */
public interface OneListenerBean {
	@NonNull
	TavernaServerListenerREST connect(@NonNull Listener listen,
			@NonNull TavernaRun run);
}