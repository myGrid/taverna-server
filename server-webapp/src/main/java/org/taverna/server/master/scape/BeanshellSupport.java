package org.taverna.server.master.scape;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.phloc.schematron.ISchematronResource;
import com.phloc.schematron.pure.SchematronResourcePure;

/**
 * This class is used to help write the beanshell scripts inside the outer
 * workflows.
 * 
 * @author Donal Fellows
 * @deprecated Do not use directly
 */
@Deprecated
class BeanshellSupport {
	@Deprecated
	static class RealizeDOs {
		String repository;
		String voidToken;
		String workDirectory;
		String objects;
		List<String> files;
		List<String> objectList;
		List<String> resolvedObjectList;

		public void shell() throws Exception {
			URL baseURL = new URL(repository + "/file");
			File wd = (workDirectory == voidToken ? new File(".") : new File(
					workDirectory));
			files = new ArrayList();
			objectList = new ArrayList();
			resolvedObjectList = new ArrayList();
			int ids = 0;
			byte[] buffer = new byte[4096];
			for (String obj : objects.split("\n")) {
				int id = ++ids;
				File f = new File(wd, "" + id);
				objectList.add(obj);
				URL url = new URL(baseURL, obj);
				resolvedObjectList.add(url.toString());

				// Download file
				InputStream is = url.openStream();
				OutputStream os = new FileOutputStream(f);
				files.add(f.toString());
				try {
					int bytesRead = 0;
					while ((bytesRead = is.read(buffer)) != -1)
						os.write(buffer, 0, bytesRead);
				} finally {
					is.close();
					os.close();
				}
			}
		}
	}

	@Deprecated
	static class AssessMetrics {
		String QLD;
		String metrics;
		List<String> failures;
		String isSatisfied;

		public void shell() throws Exception {
			File schematron = new File("schematron.xml");
			File measuresDoc = new File("measures.xml");

			PrintWriter pw = new PrintWriter(schematron, "UTF-8");
			pw.println(QLD);
			pw.close();
			pw = new PrintWriter(measuresDoc, "UTF-8");
			pw.println(metrics);
			pw.close();
			failures = new ArrayList<String>();

			ISchematronResource validator = SchematronResourcePure
					.fromFile(schematron);
			if (!validator.isValidSchematron())
				throw new Exception("invalid schematron document");
			Document result = validator
					.applySchematronValidation(new StreamSource(measuresDoc));
			NodeList nl = result.getDocumentElement().getElementsByTagNameNS(
					"http://purl.oclc.org/dsdl/svrl", "failed-assert");
			for (int i = 0; i < nl.getLength(); i++) {
				Element elem = (Element) nl.item(0);
				failures.add(elem
						.getElementsByTagNameNS(
								"http://purl.oclc.org/dsdl/svrl", "text")
						.item(0).getTextContent());
			}
			isSatisfied = "" + failures.isEmpty();
		}
	}

	@Deprecated
	static class WriteDOsToRepository {
		String errors;
		String written;
		List<String> isSatisfied;
		List<String> outputFiles;

		public void shell() throws Exception {
			StringBuilder errorsBuffer = new StringBuilder(
					"<h1>Errors:</h1><ul>");
			StringBuilder writtenBuffer = new StringBuilder("<h1>Written:</h1>");
			Iterator sat = isSatisfied.iterator();
			Iterator out = outputFiles.iterator();
			int nout = 0, nerr = 0;
			while (sat.hasNext() && out.hasNext()) {
				if (sat.next().equals("true")) {
					// FIXME
					writtenBuffer.append("<li>wrote ").append(out.next())
							.append(" back to repo</li>");
					nout++;
				} else {
					errorsBuffer.append("<li>failed to write ")
							.append(out.next()).append("</li>");
					nerr++;
				}
			}
			errors = errorsBuffer.append("</ul>Total errors: ").append(nerr)
					.toString();
			written = writtenBuffer.append("</ul>Total written: ").append(nout)
					.toString();
		}
	}
}
