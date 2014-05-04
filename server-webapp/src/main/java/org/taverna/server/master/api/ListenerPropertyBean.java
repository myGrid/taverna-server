package org.taverna.server.master.api;

import javax.annotation.Nonnull;

import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;

/**
 * Description of properties supported by {@link ListenerPropertyREST}.
 * 
 * @author Donal Fellows
 */
public interface ListenerPropertyBean extends SupportAware {
	@Nonnull
	TavernaServerListenersREST.Property connect(@Nonnull Listener listen,
			@Nonnull TavernaRun run, @Nonnull String propertyName);
}