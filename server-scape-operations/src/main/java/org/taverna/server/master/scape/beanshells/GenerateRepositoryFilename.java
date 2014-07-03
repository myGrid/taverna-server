package org.taverna.server.master.scape.beanshells;

import static org.apache.tika.mime.MimeTypes.getDefaultMimeTypes;
import static org.taverna.server.master.scape.beanshells.utils.RECache.match;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

public class GenerateRepositoryFilename extends Support<GenerateRepositoryFilename> {
	@Input
	private String repositoryDirectory, temporaryFile, contentType;
	@Output
	private String repositoryFile;
	private static SoftReference<Tika> tika;
	private static SoftReference<MimeTypes> mimes;

	private String detectMimeType(File file) throws IOException {
		Tika detector;
		synchronized (getClass()) {
			detector = tika.get();
			if (detector == null)
				tika = new SoftReference<>(detector = new Tika());
		}
		return detector.detect(file);
	}

	private String getExtension(String contentType) throws MimeTypeException {
		MimeTypes mtdb;
		synchronized (getClass()) {
			mtdb = mimes.get();
			if (mtdb == null)
				mimes = new SoftReference<>(mtdb = getDefaultMimeTypes());
		}
		return mtdb.forName(contentType).getExtension();
	}

	@Override
	public void perform() throws Exception {
		File tmp = new File(temporaryFile);
		String ext = "";
		if (!match(".[.].", tmp.getName()) && tmp.exists() && tmp.isFile()
				&& tmp.canRead()) {
			if (contentType == null)
				contentType = detectMimeType(tmp);
			ext = getExtension(contentType);
		}
		repositoryFile = new File(repositoryDirectory,
				tmp.getName() + ext).toString();
	}
}