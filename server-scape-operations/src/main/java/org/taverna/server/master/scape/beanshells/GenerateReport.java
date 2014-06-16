package org.taverna.server.master.scape.beanshells;

import static java.lang.String.format;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

public class GenerateReport extends Support<GenerateReport> {
	@Output
	private String report;
	@Input
	private boolean doWrite;
	@Input
	private List<String> objects, writtenInfo, writeErrors;
	@Input
	@Nullable
	private List<List<String>> assessErrors;
	private boolean wasCharacterise;
	private int nout, nerr;

	public GenerateReport() {
	}

	public GenerateReport(boolean characterise) {
		wasCharacterise = characterise;
	}

	@Override
	public void perform() throws Exception {
		Iterator<String> ob_it = objects.iterator();
		Iterator<String> wi_it = writtenInfo.iterator();
		Iterator<String> we_it = writeErrors.iterator();
		Iterator<List<String>> ae_it = (assessErrors != null ? assessErrors
				.iterator() : null);
		StringBuilder errorsBuffer = new StringBuilder();
		StringBuilder writtenBuffer = new StringBuilder();
		nout = nerr = 0;

		while (ob_it.hasNext() && wi_it.hasNext() && we_it.hasNext()
				&& (ae_it == null || ae_it.hasNext()))
			generateReportLine(errorsBuffer, writtenBuffer, next(ob_it),
					next(wi_it), next(we_it), next(ae_it));
		report = format(
				"<h2>Summary</h2>There were %s successful %s and %d errors."
						+ "<h2>Written</h2><ul>%s</ul>"
						+ "<h2>Errors</h2><ul>%s</ul>", nout,
				wasCharacterise ? "metadata updates" : "writes", nerr,
				writtenBuffer, errorsBuffer);
	}

	private static <T> T next(Iterator<T> iterator) {
		return iterator == null ? null : iterator.next();
	}

	private void generateReportLine(StringBuilder errorsBuffer,
			StringBuilder writtenBuffer, String ob, String wi, String we,
			List<String> ae) {
		// Errors in assessment
		if (ae != null && !ae.isEmpty()) {
			errorsBuffer.append("<li>Object ").append(ob)
					.append(" failed assessment.<ul>");
			for (String e : ae)
				errorsBuffer.append("<li>").append(e).append("</li>");
			errorsBuffer.append("</ul></li>");
			nerr++;
			return;
		}

		// Errors in write
		if (we != null && !we.isEmpty()) {
			errorsBuffer.append("<li>Object ").append(ob)
					.append(" failed in upload.<br>").append(we)
					.append("</ul></li>");
			nerr++;
			return;
		}

		// Report success
		String[] info = wi.split(";", 2);
		String src = info[0];
		String repo = info[1];
		writtenBuffer.append("<li>Object ").append(ob);
		nout++;
		if (wasCharacterise) {
			if (doWrite)
				writtenBuffer
						.append(" had its metadata updated in repository ");
			else
				writtenBuffer
						.append(" would have had its metadata updated in repository ");
			writtenBuffer.append(repo).append("</li>");
			return;
		}
		String tail = "";
		if (doWrite)
			writtenBuffer.append(" was written back to ");
		else {
			tail = " (write-back was inhibited by policy)";
			writtenBuffer.append(" would have been written back to ");
		}
		writtenBuffer.append(src).append(" in repository ").append(repo)
				.append(tail).append("</li>");
	}
}