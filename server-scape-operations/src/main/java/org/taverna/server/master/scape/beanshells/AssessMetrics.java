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

public class AssessMetrics extends Support<AssessMetrics> {
	@Input
	private String QLD;
	@Input
	private String metrics;
	@Output
	private List<String> failures;
	@Output
	private boolean isSatisfied;

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
				Element elem = (Element) nl.item(i);
				failures.add(elem
						.getElementsByTagNameNS(
								"http://purl.oclc.org/dsdl/svrl", "text")
						.item(0).getTextContent());
			}
			isSatisfied = failures.isEmpty();
		} finally {
			measuresDoc.delete();
			schematron.delete();
		}
	}
}