package org.taverna.server.master.scape.beanshells;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FetchMetadata extends Support<FetchMetadata> {
	@Input
	private String url;
	@Output
	private String contents;

	private final byte[] buffer = new byte[4096];

	private InputStream connect(String url) throws IOException {
		HttpURLConnection huc = (HttpURLConnection) new URL(url)
				.openConnection();
		huc.setConnectTimeout(2000);
		huc.setReadTimeout(10000);
		return huc.getInputStream();
	}

	@Override
	protected void op() throws Exception {
		try (ByteArrayOutputStream sw = new ByteArrayOutputStream();
				InputStream is = connect(url)) {
			int bytesRead = 0;
			while ((bytesRead = is.read(buffer)) != -1)
				sw.write(buffer, 0, bytesRead);
			contents = sw.toString();
		}
	}
}
