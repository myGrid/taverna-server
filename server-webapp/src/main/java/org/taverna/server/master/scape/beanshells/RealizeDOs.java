package org.taverna.server.master.scape.beanshells;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class RealizeDOs implements BeanshellSupport {
	static String repository;
	static String voidToken;
	static String workDirectory;
	static List<String> objects;
	List<String> files;
	List<String> objectList;
	List<String> resolvedObjectList;

	@Override
	public void shell() throws Exception {
		URL baseURL = new URL(repository + "/file");
		File wd = (workDirectory == voidToken ? new File(".") : new File(
				workDirectory));
		files = new ArrayList();
		objectList = new ArrayList();
		resolvedObjectList = new ArrayList();
		int ids = 0;
		byte[] buffer = new byte[4096];
		for (String obj : objects) {
			int id = ++ids;
			File f = new File(wd, "" + id);
			objectList.add(obj);
			URL url = new URL(baseURL, obj);
			resolvedObjectList.add(url.toString());

			// Download file
			InputStream is = url.openStream();
			OutputStream os = new FileOutputStream(f);
			files.add(f.toString());
			try {
				int bytesRead = 0;
				while ((bytesRead = is.read(buffer)) != -1)
					os.write(buffer, 0, bytesRead);
			} finally {
				is.close();
				os.close();
			}
		}
	}
}