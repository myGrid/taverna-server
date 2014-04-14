package org.taverna.server.master.api;

import javax.annotation.Nonnull;

import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;

/**
 * Description of properties supported by {@link ListenersREST}.
 * 
 * @author Donal Fellows
 */
public interface ListenersBean extends SupportAware {
	@Nonnull
	TavernaServerListenersREST connect(@Nonnull TavernaRun run);
}