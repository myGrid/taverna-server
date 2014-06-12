package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Scanner;

public class UpdateIntellectualEntityInRepository extends
		Support<UpdateIntellectualEntityInRepository> {
	@Output
	private String error, written;
	@Input
	private String src, repository, meta;
	@Input
	private boolean sat, doWrite;

	@Override
	public void perform() throws Exception {
		error = written = "";
		URL url = new URL(repository + src);
		if (!sat)
			error = "failed quality check";
		else if (!doWrite)
			written = format("%s;%s", src, repository);
		else
			writeIEUpdate(url);
	}

	private void writeIEUpdate(URL url) throws IOException, ProtocolException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod("PUT");
			conn.setDoOutput(true);
			try (Writer w = new OutputStreamWriter(conn.getOutputStream())) {
				w.write(meta);
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
				written = format("%s;%s", src, repository);
				// Ignore a successful write's response
			}
		} finally {
			conn.disconnect();
		}
	}
}
