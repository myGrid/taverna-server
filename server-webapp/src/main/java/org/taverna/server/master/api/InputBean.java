package org.taverna.server.master.api;

import javax.annotation.Nonnull;
import javax.ws.rs.core.UriInfo;

import org.taverna.server.master.ContentsDescriptorBuilder;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerInputREST;
import org.taverna.server.master.utils.FilenameUtils;

/**
 * Description of properties supported by {@link InputREST}.
 * 
 * @author Donal Fellows
 */
public interface InputBean extends SupportAware {
	@Nonnull
	TavernaServerInputREST connect(@Nonnull TavernaRun run, @Nonnull UriInfo ui);

	void setCdBuilder(@Nonnull ContentsDescriptorBuilder cd);

	void setFileUtils(@Nonnull FilenameUtils fn);
}