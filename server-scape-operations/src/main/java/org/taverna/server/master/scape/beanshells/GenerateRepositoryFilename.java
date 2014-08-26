package org.taverna.server.master.scape.beanshells;

import static org.apache.tika.mime.MimeTypes.OCTET_STREAM;
import static org.apache.tika.mime.MimeTypes.getDefaultMimeTypes;
import static org.taverna.server.master.scape.beanshells.utils.RECache.match;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.taverna.server.master.scape.beanshells.BeanshellSupport.Name;

@Name("GenerateRepositoryFilename")
public class GenerateRepositoryFilename extends
		Support<GenerateRepositoryFilename> {
	@Input
	private String repositoryDirectory, repositoryAccessUri, temporaryFile;
	@Input(required = false)
	@Output
	private String contentType;
	@Output
	private String repositoryFile;
	@Output
	private String fileAccessUri;

	private static SoftReference<Tika> tikaCache;
	private static SoftReference<MimeTypes> mtdbCache;

	private static <T> T get(Reference<T> ref) {
		if (ref == null)
			return null;
		return ref.get();
	}

	private String detectMimeType(File file) throws IOException {
		Tika tika;
		synchronized (getClass()) {
			tika = get(tikaCache);
			if (tika == null)
				tikaCache = new SoftReference<>(tika = new Tika());
		}
		return tika.detect(file);
	}

	private String getExtension(String contentType) throws MimeTypeException {
		MimeTypes mtdb;
		synchronized (getClass()) {
			mtdb = get(mtdbCache);
			if (mtdb == null)
				mtdbCache = new SoftReference<>(mtdb = getDefaultMimeTypes());
		}
		return mtdb.forName(contentType).getExtension();
	}

	@Override
	public void op() throws Exception {
		File tmp = new File(temporaryFile);
		String ext = "";
		if (tmp.exists() && tmp.isFile() && tmp.canRead()) {
			if (contentType == null)
				contentType = detectMimeType(tmp);
			if (!match("[^.]+[.][^.]+", tmp.getName()))
				ext = getExtension(contentType);
		} else if (contentType == null)
			contentType = OCTET_STREAM;
		String name = tmp.getName();
		while (name.endsWith("."))
			name = name.substring(0, name.length()-1);
		File theFile = new File(repositoryDirectory, name + ext);
		repositoryFile = theFile.getAbsolutePath();
		fileAccessUri = URI.create(repositoryAccessUri+"/").resolve(theFile.getName()).toString();
	}
}