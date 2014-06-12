package org.taverna.server.master.scape.beanshells;

import java.io.File;

public class GenerateRepositoryFilename extends Support<GenerateRepositoryFilename> {
	@Input
	private String repositoryDirectory, temporaryFile;
	@Output
	private String repositoryFile;

	@Override
	public void perform() throws Exception {
		repositoryFile = new File(repositoryDirectory,
				new File(temporaryFile).getName()).toString();
	}
}