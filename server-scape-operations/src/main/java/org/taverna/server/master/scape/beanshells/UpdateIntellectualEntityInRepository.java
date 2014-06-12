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
	private String error, written;
	String src, repository, doWrite, sat, meta;

	@Override
	public void perform() throws Exception {
		error = written = "";
		URL url = new URL(repository + src);
		if (!"true".equals(sat))
			error = "failed quality check";
		else if (!"true".equals(doWrite))
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
				error = "failed during write: " + conn.getResponseMessage()
						+ "<br><pre>";
				try (Scanner sc = new Scanner(conn.getErrorStream())) {
					while (sc.hasNextLine())
						error += "\n" + sc.nextLine();
				}
				error += "\n</pre>";
			} else {
				written = format("%s;%s", src, repository);
				// # Ignore a successful write's response
			}
		} finally {
			conn.disconnect();
		}
	}

	@Override
	public UpdateIntellectualEntityInRepository init(String name, String value) {
		switch (name) {
		case "src":
			src = value;
			break;
		case "repository":
			repository = value;
			break;
		case "doWrite":
			doWrite = value;
			break;
		case "sat":
			sat = value;
			break;
		case "meta":
			meta = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public String getResult(String name) {
		switch (name) {
		case "error":
			return error;
		case "written":
			return written;
		}
		throw new UnsupportedOperationException();
	}
}
