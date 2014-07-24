package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

public class UpdateIntellectualEntityInRepository extends
		Support<UpdateIntellectualEntityInRepository> {
	@Output
	private String error, written;
	@Input
	private String src;
	@Input
	private String repository;
	@Input
	private String meta;
	@Input(required = false)
	private List<String> sat;
	@Input
	private boolean doWrite;

	private void success() {
		written = format("%s;%s", src, repository);
	}

	@Override
	public void op() throws Exception {
		error = written = "";
		boolean anySat = false;
		for (String s : sat)
			if (Boolean.parseBoolean(s)) {
				anySat = true;
				break;
			}
		// TODO Should this be a NEW ID?
		URL url = new URL(new URL(repository), src);
		if (!anySat)
			error = "failed quality check";
		else if (!doWrite)
			success();
		else
			writeIEUpdate(url, meta);
	}

	private void writeIEUpdate(URL url, String content) throws IOException,
			ProtocolException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod("PUT");
			conn.setDoOutput(true);
			try (Writer w = new OutputStreamWriter(conn.getOutputStream())) {
				w.write(content);
			}
			if (conn.getResponseCode() >= 400) {
				StringBuilder sb = new StringBuilder("failed during write: ")
						.append(conn.getResponseMessage()).append("<br><pre>");
				try (Scanner sc = new Scanner(conn.getErrorStream())) {
					while (sc.hasNextLine())
						sb.append("\n").append(sc.nextLine());
				}
				error = sb.append("\n</pre>").toString();
			} else {
				success();
				// Ignore a successful write's response
			}
		} finally {
			conn.disconnect();
		}
	}
}
