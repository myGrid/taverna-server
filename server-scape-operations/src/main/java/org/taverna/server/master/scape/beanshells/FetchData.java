package org.taverna.server.master.scape.beanshells;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchData extends Support<FetchData> {
	@Input
	private String url;
	@Input
	@Output
	private String filename;
	private final byte[] buffer = new byte[4096];

	private InputStream connect(String url) throws IOException {
		HttpURLConnection huc = (HttpURLConnection) new URL(url)
				.openConnection();
		huc.setConnectTimeout(2000);
		huc.setReadTimeout(20000);
		return huc.getInputStream();
	}

	// Download file
	@Override
	protected void op() throws Exception {
		try (InputStream is = connect(url);
				OutputStream os = new FileOutputStream(filename)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				os.write(buffer, 0, bytesRead);
		}
	}
}
