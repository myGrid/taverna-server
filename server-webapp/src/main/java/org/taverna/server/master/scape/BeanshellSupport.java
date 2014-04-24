package org.taverna.server.master.scape;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.phloc.schematron.ISchematronResource;
import com.phloc.schematron.pure.SchematronResourcePure;

import eu.scape_project.model.Identifier;
import eu.scape_project.model.IntellectualEntity;
import eu.scape_project.model.Representation;
import eu.scape_project.util.ScapeMarshaller;

/**
 * This class is used to help write the beanshell scripts inside the outer
 * workflows.
 * 
 * @author Donal Fellows
 * @deprecated Do not use directly
 */
@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class RealizeDOs {
	static String repository;
	static String voidToken;
	static String workDirectory;
	static String objects;
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
@SuppressWarnings({ "rawtypes", "unchecked" })
class AssessMetrics {
	static String QLD;
	static String metrics;
	List<String> failures;
	String isSatisfied;

	public void shell() throws Exception {
		String id = java.util.UUID.randomUUID().toString();
		File schematron = new File("schematron_"+id+".xml");
		File measuresDoc = new File("measures_"+id+".xml");

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
		Document result = validator.applySchematronValidation(new StreamSource(
				measuresDoc));
		NodeList nl = result.getDocumentElement().getElementsByTagNameNS(
				"http://purl.oclc.org/dsdl/svrl", "failed-assert");
		for (int i = 0; i < nl.getLength(); i++) {
			Element elem = (Element) nl.item(0);
			failures.add(elem
					.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl",
							"text").item(0).getTextContent());
		}
		isSatisfied = "" + failures.isEmpty();
	}
}

@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class WriteDOsToRepository {
	String errors;
	String written;
	static String doWrite;
	static String repository;
	static List<String> isSatisfied;
	static List<String> outputFiles;
	static List<String> digitalObjects;
	static List<String> metadata;

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
					java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url
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
				writtenBuffer.append(String.format(
						(reallyWrite ? "<li>wrote %s from %s back to repo %s</li>"
								: "<li>would write %s from %s back to repo %s</li>"),
						dst, src, repository));
				nout++;
			} else {
				errorsBuffer.append(String.format(
						"<li>did not write %s from %w back to repo %s; %s</li>",
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

@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class ConstructNewMetadata {
	static String originalMetadata;
	static String newInformation;
	static String generatedFilename;
	static String creator;
	static String contentType;
	String newMetadata;

	public void shell() throws Exception {
		ScapeMarshaller sm = ScapeMarshaller.newInstance();
		String id1 = UUID.randomUUID().toString();
		String id2 = UUID.randomUUID().toString();
		File file = new File(generatedFilename);

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(newInformation));
		Element newMeta = builder.parse(is).getDocumentElement();

		IntellectualEntity original = (IntellectualEntity) sm
				.deserialize(new ByteArrayInputStream(originalMetadata
						.getBytes()));
		IntellectualEntity.Builder ie = new IntellectualEntity.Builder(original);

		ie.representation(new Representation.Builder()
				.identifier(new Identifier(id2))
				.technical(newMeta)
				.title("output from " + creator)
				.file(new eu.scape_project.model.File.Builder()
						.identifier(new Identifier(id1)).mimetype(contentType)
						.uri(file.toURI()).filename(file.getName()).build())
				.build());

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(ie.build(), baos);
		newMetadata = baos.toString();
	}
}
