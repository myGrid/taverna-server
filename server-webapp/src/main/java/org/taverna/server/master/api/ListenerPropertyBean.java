package org.taverna.server.master.api;

import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link ListenerPropertyREST}.
 * 
 * @author Donal Fellows
 */
public interface ListenerPropertyBean extends SupportAware {
	@NonNull
	TavernaServerListenersREST.Property connect(@NonNull Listener listen,
			@NonNull TavernaRun run, @NonNull String propertyName);
}