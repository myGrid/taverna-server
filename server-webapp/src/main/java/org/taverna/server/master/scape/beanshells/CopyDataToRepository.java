package org.taverna.server.master.scape.beanshells;

import java.io.File;
import java.nio.file.Files;

@Deprecated
class CopyDataToRepository implements BeanshellSupport {
	static String doWrite, temporaryFile, repositoryFile;

	@Override
	public void shell() throws Exception {
		if ("true".equals(doWrite))
			Files.copy(new File(temporaryFile).toPath(), new File(
					repositoryFile).toPath());
	}
}