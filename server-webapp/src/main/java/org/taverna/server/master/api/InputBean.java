package org.taverna.server.master.api;

import javax.ws.rs.core.UriInfo;

import org.taverna.server.master.ContentsDescriptorBuilder;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerInputREST;
import org.taverna.server.master.utils.FilenameUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link InputREST}.
 * 
 * @author Donal Fellows
 */
public interface InputBean extends SupportAware {
	@NonNull
	TavernaServerInputREST connect(@NonNull TavernaRun run, @NonNull UriInfo ui);

	void setCdBuilder(ContentsDescriptorBuilder cd);

	void setFileUtils(FilenameUtils fn);
}