package org.taverna.server.master.scape.beanshells;

import java.util.Iterator;
import java.util.List;

@Deprecated
@SuppressWarnings({ "rawtypes" })
class GenerateReport implements BeanshellSupport {
	String report, errors, written;
	static String doWrite;
	static List<String> objects, writtenInfo, writeErrors;
	static List<List<String>> assessErrors;

	@Override
	public void shell() throws Exception {
		Iterator ob_it = objects.iterator();
		Iterator wi_it = writtenInfo.iterator();
		Iterator we_it = writeErrors.iterator();
		Iterator ae_it = assessErrors.iterator();
		StringBuilder errorsBuffer = new StringBuilder();
		StringBuilder writtenBuffer = new StringBuilder("<h1>Written:</h1><ul>");
		boolean reallyWrite = "true".equals(doWrite);
		int nout = 0, nerr = 0;

		while (ob_it.hasNext() && wi_it.hasNext() && we_it.hasNext()
				&& ae_it.hasNext()) {
			String ob = (String) ob_it.next();
			String wi = (String) wi_it.next();
			String we = (String) we_it.next();
			List ae = (List) ae_it.next();

			if (!ae.isEmpty()) {
				errorsBuffer.append("<li>Object ").append(ob)
						.append(" failed assessment.<ul>");
				for (Object e : ae)
					errorsBuffer.append("<li>").append(e).append("</li>");
				errorsBuffer.append("</ul></li>");
				nerr++;
			} else if (we != null && !we.isEmpty()) {
				errorsBuffer.append("<li>Object ").append(ob)
						.append(" failed in upload.<br>").append(we)
						.append("</ul></li>");
				nerr++;
			} else {
				String[] info = wi.split(";", 2);
				String src = info[0];
				String repo = info[1];
				writtenBuffer.append("<li>Object ").append(ob);
				String tail = "";
				if (reallyWrite) {
					writtenBuffer.append(" was written back to ");
				} else {
					tail = " (write-back inhibited)";
					writtenBuffer.append(" would have been written back to ");
				}
				writtenBuffer.append(src).append(" in repository ")
						.append(repo).append(tail).append("</li>");
				nout++;
			}
		}
		report = "There were " + nout + " successful writes and " + nerr
				+ " errors.<h1>Written:</h1><ul>" + writtenBuffer
				+ "</ul><h1>Errors:</h1><ul>" + errorsBuffer + "</ul>";
	}
}