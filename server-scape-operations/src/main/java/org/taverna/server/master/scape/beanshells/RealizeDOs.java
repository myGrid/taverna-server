package org.taverna.server.master.scape.beanshells;

import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.net.URLEncoder.encode;
import static java.util.Arrays.asList;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RealizeDOs extends Support<RealizeDOs> {
	private static final String pid;
	private static volatile int ids;
	static {
		pid = getRuntimeMXBean().getName().replaceFirst("@.*", "");
		ids = 0;
	}
	@Input
	private String repository;
	@Input(required = false)
	private String workDirectory;
	@Input
	private String objects;

	@Output
	private List<List<String>> files;
	@Output
	private List<List<String>> objectList;
	@Output
	private List<List<String>> resolvedObjectList;
	@Output
	private List<String> entityIdList;
	@Output
	private List<String> entityUriList;
	@Output
	private List<String> representationUriList;

	private Map<String, List<URL>> objectsInEntity;

	URL entBase, repBase, fileBase;

	private static File getCWD() {
		return Paths.get("").toAbsolutePath().toFile();
	}

	@Override
	protected void op() throws Exception {
		initOutputs();
		File wd = (workDirectory == null ? getCWD() : new File(workDirectory));
		if (!repository.endsWith("/"))
			repository += "/";
		URL rURL = new URL(repository);
		entBase = new URL(rURL, "entity/");
		repBase = new URL(rURL, "representation/");
		fileBase = new URL(rURL, "file/");
		objectsInEntity = new HashMap<>();
		for (String obj : objects.split("\n"))
			realizeOneDO(wd, ++ids, obj);
		for (String rep : representationUriList)
			realizeDOFiles(wd, ++ids, rep);
	}

	/** Set up the output lists */
	private void initOutputs() {
		files = new ArrayList<>();
		objectList = new ArrayList<>();
		resolvedObjectList = new ArrayList<>();
		representationUriList = new ArrayList<>();
		entityIdList = new ArrayList<>();
		entityUriList = new ArrayList<>();
	}

	private void realizeOneDO(File wd, int id, String obj)
			throws MalformedURLException {
		List<String> objBits = cleanUpObjectHandle(obj);
		URL url = resolveContent(objBits);
		URL repUrl = resolveRepresentation(objBits);
		String repkey = repUrl.toString();
		URL entUrl = resolveEntity(objBits);
		if (!objectsInEntity.containsKey(repkey)) {
			objectsInEntity.put(repkey, new ArrayList<URL>());
			entityIdList.add(join(objBits.subList(0, 1)));
			entityUriList.add(entUrl.toString());
			representationUriList.add(repkey);
		}
		objectsInEntity.get(repkey).add(url);
	}

	private void realizeDOFiles(File wd, int id, String obj) {
		int idx = resolvedObjectList.size();
		resolvedObjectList.add(new ArrayList<String>());
		files.add(new ArrayList<String>());
		List<String> objs;
		objectList.add(objs = new ArrayList<String>());
		for (URL url : objectsInEntity.get(obj)) {
			objs.add(join(cleanUpObjectHandle(url.toString())));
			resolvedObjectList.get(idx).add(url.toString());
			files.get(idx).add(
					new File(wd, "data." + pid + "." + id).toString());
		}
	}

	private List<String> cleanUpObjectHandle(String obj) {
		List<String> bits = asList(obj.split("/"));
		return bits.subList(bits.size() - 3, bits.size());
	}

	@SuppressWarnings("deprecation")
	private String join(List<String> list) {
		StringBuilder sb = new StringBuilder();
		String sep = "";
		for (String bit : list) {
			try {
				sb.append(sep).append(encode(bit, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// Bleah fallback; should be unreachable
				sb.append(sep).append(encode(bit));
			}
			sep = "/";
		}
		return sb.toString();
	}

	protected URL resolveEntity(List<String> obj) throws MalformedURLException {
		if (obj.size() < 1)
			return new URL(entBase, join(obj));
		return new URL(entBase, join(obj.subList(0, 1)));
	}

	protected URL resolveRepresentation(List<String> obj)
			throws MalformedURLException {
		if (obj.size() < 2)
			return new URL(repBase, join(obj));
		return new URL(repBase, join(obj.subList(0, 2)));
	}

	protected URL resolveContent(List<String> obj) throws MalformedURLException {
		if (obj.size() < 3)
			return new URL(fileBase, join(obj));
		return new URL(fileBase, join(obj.subList(0, 3)));
	}
}