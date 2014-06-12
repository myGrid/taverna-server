package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
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

public class RealizeDOs extends Support<RealizeDOs> {
	@Input
	private String repository;
	@Input
	private String workDirectory;
	@Input
	private List<String> objects;

	@Output
	private List<String> files;
	@Output
	private List<String> objectList;
	@Output
	private List<String> resolvedObjectList;
	@Output
	private List<String> representationList;
	@Output
	private List<String> representationUriList;

	private final byte[] buffer = new byte[4096];

	@Override
	public void perform() throws Exception {
		URL baseURL = new URL(repository + "/file");
		URL repURL = new URL(repository + "/representation");
		File wd = (workDirectory == null ? new File(".") : new File(
				workDirectory));
		files = new ArrayList<>();
		objectList = new ArrayList<>();
		resolvedObjectList = new ArrayList<>();
		representationList = new ArrayList<>();
		representationUriList = new ArrayList<>();
		int ids = 0;
		for (String obj : objects)
			realizeOneDO(baseURL, repURL, wd, ++ids, obj);
	}

	private void realizeOneDO(URL baseURL, URL repURL, File wd, int id, String obj)
			throws MalformedURLException, IOException, FileNotFoundException {
		objectList.add(obj);
		URL url = new URL(baseURL, obj);
		URL repUrl = new URL(repURL, format("%s/%s", (Object[]) obj.split("/")));
		resolvedObjectList.add(url.toString());
		representationUriList.add(repUrl.toString());
		File f = new File(wd, "" + id);

		// Download file
		try (InputStream is = url.openStream();
				OutputStream os = new FileOutputStream(f)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				os.write(buffer, 0, bytesRead);
			files.add(f.toString());
		}

		// Download metadata
		try (ByteArrayOutputStream sw = new ByteArrayOutputStream();
				InputStream is = repUrl.openStream()) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				sw.write(buffer, 0, bytesRead);
			representationList.add(sw.toString());
		}
	}
}