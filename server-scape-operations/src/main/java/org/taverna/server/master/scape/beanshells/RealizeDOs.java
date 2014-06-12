package org.taverna.server.master.scape.beanshells;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

class RealizeDOs extends Support<RealizeDOs> {
	private String repository;
	private String workDirectory;
	private List<String> objects;
	private List<String> files;
	private List<String> objectList;
	private List<String> resolvedObjectList;
	private final byte[] buffer = new byte[4096];

	@Override
	public void perform() throws Exception {
		URL baseURL = new URL(repository + "/file");
		File wd = (workDirectory == null ? new File(".") : new File(
				workDirectory));
		files = new ArrayList<>();
		objectList = new ArrayList<>();
		resolvedObjectList = new ArrayList<>();
		int ids = 0;
		for (String obj : objects)
			realizeOneDO(baseURL, wd, ++ids, obj);
	}

	private void realizeOneDO(URL baseURL, File wd, int id, String obj)
			throws MalformedURLException, IOException, FileNotFoundException {
		objectList.add(obj);
		URL url = new URL(baseURL, obj);
		resolvedObjectList.add(url.toString());
		File f = new File(wd, "" + id);

		// Download file
		try (InputStream is = url.openStream();
				OutputStream os = new FileOutputStream(f)) {
			files.add(f.toString());
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				os.write(buffer, 0, bytesRead);
		}
	}

	@Override
	public RealizeDOs init(String name, String value) {
		switch (name) {
		case "repository":
			repository = value;
			break;
		case "workDirectory":
			workDirectory = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public RealizeDOs init(String name, List<String> value) {
		switch (name) {
		case "objects":
			objects = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public List<String> getResultList(String name) {
		switch (name) {
		case "files":
			return files;
		case "objectList":
			return objectList;
		case "resolvedObjectList":
			return resolvedObjectList;
		}
		throw new UnsupportedOperationException();
	}
}