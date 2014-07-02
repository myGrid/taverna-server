package org.taverna.server.master.scape.beanshells;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RealizeDOs extends Support<RealizeDOs> {
	private static final String pid;
	private static volatile int ids;
	static {
		pid = ManagementFactory.getRuntimeMXBean().getName().replaceFirst("@.*", "");
		ids = 0;
	}
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
	private List<String> entityList;
	@Output
	private List<String> entityUriList;
	@Output
	private List<String> representationList;
	@Output
	private List<String> representationUriList;

	private final byte[] buffer = new byte[4096];
	URL entBase, repBase, fileBase;

	@Override
	public void perform() throws Exception {
		File wd = (workDirectory == null ? new File(".") : new File(
				workDirectory));
		files = new ArrayList<>();
		objectList = new ArrayList<>();
		resolvedObjectList = new ArrayList<>();
		representationList = new ArrayList<>();
		representationUriList = new ArrayList<>();
		entityList = new ArrayList<>();
		entityUriList = new ArrayList<>();
		if (!repository.endsWith("/"))
			repository += "/";
		URL rURL = new URL(repository);
		entBase = new URL(rURL, "entity/");
		repBase = new URL(rURL, "representation/");
		fileBase = new URL(rURL, "file/");
		for (String obj : objects)
			realizeOneDO(wd, ++ids, obj);
	}

	private InputStream connect(URL url) throws IOException {
		HttpURLConnection huc = (HttpURLConnection) url.openConnection();
		huc.setConnectTimeout(2000);
		huc.setReadTimeout(10000);
		return huc.getInputStream();
	}
	private void realizeOneDO(File wd, int id, String obj)
			throws MalformedURLException, IOException, FileNotFoundException {
		List<String>objBits = cleanUpObjectHandle(obj);
		objectList.add(obj);
		URL url = resolveContent(objBits);
		URL repUrl = resolveRepresentation(objBits);
		URL entUrl = resolveEntity(objBits);
		resolvedObjectList.add(url.toString());
		representationUriList.add(repUrl.toString());
		entityUriList.add(entUrl.toString());
		File f = new File(wd, "data." + pid + "." + id);

		// Download file
		try (InputStream is = connect(url);
				OutputStream os = new FileOutputStream(f)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				os.write(buffer, 0, bytesRead);
			files.add(f.toString());
		}

		// Download metadata
		try (ByteArrayOutputStream sw = new ByteArrayOutputStream();
				InputStream is = connect(repUrl)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				sw.write(buffer, 0, bytesRead);
			representationList.add(sw.toString());
		}

		// Download metadata
		try (ByteArrayOutputStream sw = new ByteArrayOutputStream();
				InputStream is = connect(entUrl)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				sw.write(buffer, 0, bytesRead);
			entityList.add(sw.toString());
		}
	}

	private List<String> cleanUpObjectHandle(String obj) {
		List<String> bits = Arrays.asList(obj.split("/"));
		return bits.subList(bits.size()-3, bits.size());
	}

	private String join(List<String> list) {
		StringBuilder sb = new StringBuilder();
		String sep = "";
		for (String bit : list) {
			sb.append(sep).append(bit);
			sep = "/";
		}
		return sb.toString();
	}

	public URL resolveEntity(List<String> obj) throws MalformedURLException {
		if (obj.size() < 1)
			return new URL(entBase, join(obj));
		return new URL(entBase, join(obj.subList(0, 1)));
	}

	public URL resolveRepresentation(List<String> obj) throws MalformedURLException {
		if (obj.size() < 2)
			return new URL(repBase, join(obj));
		return new URL(repBase, join(obj.subList(0, 2)));
	}

	public URL resolveContent(List<String> obj) throws MalformedURLException {
		if (obj.size() < 3)
			return new URL(fileBase, join(obj));
		return new URL(fileBase, join(obj.subList(0, 3)));
	}
}