package org.taverna.server.master.scape.beanshells;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@Deprecated
public class UpdateIntellectualEntityInRepository implements BeanshellSupport {
	String error, written;
	static String src, repository, doWrite, sat, meta;

	@Override
	public void shell() throws Exception {
		error = written = "";
		URL url = new URL(repository + src);
		if (!"true".equals(sat)) {
			error = "failed quality check";
		} else if (!"true".equals(doWrite)) {
			written = String.format("%s;%s", new Object[]{src, repository});
		} else {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			try {
				conn.setRequestMethod("PUT");
				conn.setDoOutput(true);
				Writer w = new OutputStreamWriter(conn.getOutputStream());
				w.write(meta);
				w.close();
				if (conn.getResponseCode() >= 400) {
					error = "failed during write: " + conn.getResponseMessage()
							+ "<br><pre>";
					Scanner sc = new Scanner(conn.getErrorStream());
					while (sc.hasNextLine())
						error += "\n" + sc.nextLine();
					sc.close();
					error += "\n</pre>";
				} else {
					written = String.format("%s;%s", new Object[]{src, repository});
					//# Ignore a successful write's response
				}
			} finally {
				conn.disconnect();
			}
		}
	}
}
