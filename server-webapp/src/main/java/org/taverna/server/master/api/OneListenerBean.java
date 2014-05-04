package org.taverna.server.master.api;

import javax.annotation.Nonnull;

import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST.TavernaServerListenerREST;

/**
 * Description of properties supported by {@link InputREST}.
 * 
 * @author Donal Fellows
 */
public interface OneListenerBean {
	@Nonnull
	TavernaServerListenerREST connect(@Nonnull Listener listen,
			@Nonnull TavernaRun run);
}