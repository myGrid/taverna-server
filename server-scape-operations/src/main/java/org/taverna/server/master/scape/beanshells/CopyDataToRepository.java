package org.taverna.server.master.scape.beanshells;

import static java.nio.file.Files.copy;

import java.io.File;

public class CopyDataToRepository extends Support<CopyDataToRepository> {
	@Input
	private boolean doWrite;
	@Input
	private Boolean isSatisfied;
	@Input
	private String temporaryFile;
	@Input
	@Output
	private String repositoryFile;

	@Override
	protected void op() throws Exception {
		if (doWrite && isSatisfied)
			copy(new File(temporaryFile).toPath(),
					new File(repositoryFile).toPath());
	}
}