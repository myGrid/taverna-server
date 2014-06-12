package org.taverna.server.master.scape.beanshells;

import static com.phloc.schematron.pure.SchematronResourcePure.fromFile;
import static java.util.UUID.randomUUID;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.phloc.schematron.ISchematronResource;

class AssessMetrics extends Support<AssessMetrics> {
	private String QLD;
	private String metrics;
	private List<String> failures;
	private String isSatisfied;

	@Override
	public void perform() throws Exception {
		String id = randomUUID().toString();
		File schematron = new File("schematron_" + id + ".xml");
		File measuresDoc = new File("measures_" + id + ".xml");

		try (PrintWriter pw = new PrintWriter(schematron, "UTF-8")) {
			pw.println(QLD);
		}
		try (PrintWriter pw = new PrintWriter(measuresDoc, "UTF-8")) {
			pw.println(metrics);
		}
		failures = new ArrayList<>();

		try {
			ISchematronResource validator = fromFile(schematron);
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
		} finally {
			measuresDoc.delete();
			schematron.delete();
		}
	}

	@Override
	public AssessMetrics init(String name, String value) {
		switch (name) {
		case "QLD":
			QLD = value;
			break;
		case "metrics":
			metrics = value;
			break;
		default:
			throw new UnsupportedOperationException();
		}
		return this;
	}

	@Override
	public String getResult(String name) {
		switch (name) {
		case "isSatisfied":
			return isSatisfied;
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public List<String> getResultList(String name) {
		switch (name) {
		case "failures":
			return failures;
		}
		throw new UnsupportedOperationException();
	}
}