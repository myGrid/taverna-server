package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.util.Iterator;
import java.util.List;

public class GenerateReport extends Support<GenerateReport> {
	@Output
	private String report;
	@Input
	private boolean doWrite;
	@Input
	private List<String> objects, writtenInfo, writeErrors;
	@Input
	private List<List<String>> assessErrors;
	private int nout, nerr;

	@Override
	public void perform() throws Exception {
		Iterator<String> ob_it = objects.iterator();
		Iterator<String> wi_it = writtenInfo.iterator();
		Iterator<String> we_it = writeErrors.iterator();
		Iterator<List<String>> ae_it = assessErrors.iterator();
		StringBuilder errorsBuffer = new StringBuilder();
		StringBuilder writtenBuffer = new StringBuilder("<h1>Written:</h1><ul>");
		nout = nerr = 0;

		while (ob_it.hasNext() && wi_it.hasNext() && we_it.hasNext()
				&& ae_it.hasNext())
			generateReportLine(errorsBuffer, writtenBuffer, doWrite,
					ob_it.next(), wi_it.next(), we_it.next(), ae_it.next());
		report = format(
				"<h2>Summary</h2>There were %s successful writes and %d errors."
						+ "<h2>Written</h2><ul>%s<ul>"
						+ "<h2>Errors</h2><ul>%s</ul>", nout, nerr,
				writtenBuffer, errorsBuffer);
	}

	private void generateReportLine(StringBuilder errorsBuffer,
			StringBuilder writtenBuffer, boolean reallyWrite, String ob,
			String wi, String we, List<String> ae) {
		if (!ae.isEmpty()) {
			errorsBuffer.append("<li>Object ").append(ob)
					.append(" failed assessment.<ul>");
			for (String e : ae)
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
			if (reallyWrite)
				writtenBuffer.append(" was written back to ");
			else {
				tail = " (write-back inhibited)";
				writtenBuffer.append(" would have been written back to ");
			}
			writtenBuffer.append(src).append(" in repository ")
					.append(repo).append(tail).append("</li>");
			nout++;
		}
	}
}