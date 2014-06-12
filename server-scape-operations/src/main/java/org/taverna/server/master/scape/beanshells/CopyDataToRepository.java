package org.taverna.server.master.scape.beanshells;

import static java.nio.file.Files.copy;

import java.io.File;

public class CopyDataToRepository extends Support<CopyDataToRepository> {
	private boolean doWrite;
	private String temporaryFile;
	private String repositoryFile;

	@Override
	public void perform() throws Exception {
		if (doWrite)
			copy(new File(temporaryFile).toPath(),
					new File(repositoryFile).toPath());
	}

	@Override
	public CopyDataToRepository init(String name, String value) {
		switch (name) {
		case "doWrite":
			doWrite = "true".equals(value);
			break;
		case "temporaryFile":
			temporaryFile = value;
			break;
		case "repositoryFile":
			repositoryFile = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}
}