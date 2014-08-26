package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;

import org.taverna.server.master.scape.beanshells.BeanshellSupport.Name;

@Name("AddRepresentationToRepository")
public class UpdateIntellectualEntityInRepository extends
		Support<UpdateIntellectualEntityInRepository> {
	@Output
	private String error, written, url, response;
	@Input
	private String representationId, entityId;
	@Input
	private String repository;
	@Input
	private String meta;
	@Input(required = false)
	private List<String> sat;
	@Input
	private boolean doWrite;
	@Input(required = false)
	private String httpOperation = "PUT";

	private StringBuilder resp;

	private void success() {
		written = format("%s/%s;%s", entityId, representationId, repository);
	}

	@Override
	public void op() throws Exception {
		error = written = "";
		resp = new StringBuilder();
		boolean anySat = false;
		for (String s : sat)
			if (Boolean.parseBoolean(s)) {
				anySat = true;
				break;
			}
		URL url = new URL(new URL(repository + "/representation/"), entityId);
		this.url = url.toString();
		try {
			if (!anySat)
				error = "failed quality check";
			else if (!doWrite)
				success();
			else
				writeIEUpdate(url, meta);
		} finally {
			response = resp.toString();
		}
	}

	private void writeIEUpdate(URL url, String content) throws IOException,
			ProtocolException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod(httpOperation);
			conn.addRequestProperty("Content-Type", "application/xml");
			conn.setDoOutput(true);
			try (Writer w = new OutputStreamWriter(conn.getOutputStream())) {
				w.write(content);
			}
			resp.append("HTTP ").append(conn.getResponseCode()).append(" ")
					.append(conn.getResponseMessage()).append("\n");
			for (Entry<String, List<String>> header : conn.getHeaderFields()
					.entrySet())
				if (header.getKey() != null)
					for (String data : header.getValue())
						resp.append(header.getKey()).append(": ").append(data)
								.append("\n");
			if (conn.getResponseCode() >= 400) {
				StringBuilder sb = new StringBuilder("failed during write: ")
						.append(conn.getResponseMessage()).append("<br><pre>");
				try (Scanner sc = new Scanner(conn.getErrorStream())) {
					while (sc.hasNextLine()) {
						String line = sc.nextLine();
						resp.append("\n").append(line);
						sb.append("\n").append(line);
					}
				}
				error = sb.append("\n</pre>").toString();
			} else {
				try (Scanner sc = new Scanner(conn.getInputStream())) {
					while (sc.hasNextLine())
						resp.append("\n").append(sc.nextLine());
				}
				success();
			}
		} finally {
			resp.append("\n");
			conn.disconnect();
		}
	}
}
