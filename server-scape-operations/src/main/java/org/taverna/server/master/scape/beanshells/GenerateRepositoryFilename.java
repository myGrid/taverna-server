package org.taverna.server.master.scape.beanshells;

import java.io.File;

public class GenerateRepositoryFilename extends Support<GenerateRepositoryFilename> {
	private String repositoryDirectory, temporaryFile;
	private String repositoryFile;

	@Override
	public void perform() throws Exception {
		repositoryFile = new File(repositoryDirectory,
				new File(temporaryFile).getName()).toString();
	}

	@Override
	public GenerateRepositoryFilename init(String name, String value) {
		switch (name) {
		case "repositoryDirectory":
			repositoryDirectory = value;
			break;
		case "temporaryFile":
			temporaryFile = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public String getResult(String name) {
		switch (name) {
		case "repositoryFile":
			return repositoryFile;
		}
		throw new UnsupportedOperationException();
	}
}