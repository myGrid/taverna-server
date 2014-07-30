package org.taverna.server.master.scape.beanshells;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.move;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.File;
import java.nio.file.Path;

public class CopyDataToRepository extends Support<CopyDataToRepository> {
	@Input
	private Boolean isSatisfied;
	@Input
	private String temporaryFile;
	@Input
	@Output
	private String repositoryFile;

	@Override
	protected void op() throws Exception {
		Path from = new File(temporaryFile).toPath();
		Path to = new File(repositoryFile).toPath();
		if (isSatisfied)
			try {
				move(from, to, ATOMIC_MOVE);
			} catch (Exception e) {
				copy(from, to);
			}
	}
}