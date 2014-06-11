package org.taverna.server.master.scape.beanshells;

import java.io.File;

@Deprecated
class GenerateRepositoryFilename implements BeanshellSupport {
	static String repositoryDirectory, temporaryFile;
	String repositoryFile;

	@Override
	public void shell() throws Exception {
		repositoryFile = new File(repositoryDirectory,
				new File(temporaryFile).getName()).toString();
	}
}