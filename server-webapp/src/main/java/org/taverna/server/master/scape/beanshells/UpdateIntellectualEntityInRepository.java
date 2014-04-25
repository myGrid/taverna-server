package org.taverna.server.master.scape.beanshells;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

@Deprecated
@SuppressWarnings({ "rawtypes" })
class UpdateIntellectualEntityInRepository implements BeanshellSupport {
	String errors;
	String written;
	static String doWrite;
	static String repository;
	static List<String> isSatisfied;
	static List<String> outputFiles;
	static List<String> digitalObjects;
	static List<String> metadata;

	@Override
	public void shell() throws Exception {
		Iterator sat = isSatisfied.iterator();
		Iterator out = outputFiles.iterator();
		Iterator dos = digitalObjects.iterator();
		Iterator mds = metadata.iterator();
		StringBuilder errorsBuffer = new StringBuilder("<h1>Errors:</h1><ul>");
		StringBuilder writtenBuffer = new StringBuilder("<h1>Written:</h1><ul>");
		boolean reallyWrite = "true".equals(doWrite);
		int nout = 0, nerr = 0;
		while (sat.hasNext() && out.hasNext() && mds.hasNext()) {
			Object src = dos.next();
			Object dst = out.next();
			String error = null;
			if (sat.next().equals("true")) {
				if (reallyWrite) {
					URL url = new URL(repository + src);
					HttpURLConnection conn = (HttpURLConnection) url
							.openConnection();
					conn.setRequestMethod("PUT");
					conn.setDoOutput(true);
					Writer w = new OutputStreamWriter(conn.getOutputStream());
					w.write((String) mds.next());
					w.close();
					if (conn.getResponseCode() >= 400) {
						error = "failed during write: "
								+ conn.getResponseMessage() + "<pre>";
						Scanner sc = new Scanner(conn.getErrorStream());
						while (sc.hasNextLine())
							error += "\n" + sc.nextLine();
						sc.close();
						error += "\n</pre>";
					}
				}
			} else {
				error = "failed quality check";
			}
			if (error == null) {
				writtenBuffer
						.append(String
								.format((reallyWrite ? "<li>wrote %s from %s back to repo %s</li>"
										: "<li>would write %s from %s back to repo %s</li>"),
										dst, src, repository));
				nout++;
			} else {
				errorsBuffer
						.append(String
								.format("<li>did not write %s from %w back to repo %s; %s</li>",
										dst, src, repository, error));
				nerr++;
			}
		}
		errors = errorsBuffer.append("</ul>Total errors: ").append(nerr)
				.toString();
		written = writtenBuffer.append("</ul>Total written: ").append(nout)
				.toString();
	}
}