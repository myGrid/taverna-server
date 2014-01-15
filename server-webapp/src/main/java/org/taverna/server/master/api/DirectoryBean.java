package org.taverna.server.master.api;

import org.taverna.server.master.rest.TavernaServerDirectoryREST;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.utils.FilenameUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Description of properties supported by {@link DirectoryREST}.
 * 
 * @author Donal Fellows
 */
public interface DirectoryBean extends SupportAware {
	void setFileUtils(FilenameUtils fileUtils);

	@NonNull
	TavernaServerDirectoryREST connect(@NonNull TavernaRun run);
}