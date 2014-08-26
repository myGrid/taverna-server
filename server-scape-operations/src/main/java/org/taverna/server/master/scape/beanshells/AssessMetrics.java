package org.taverna.server.master.scape.beanshells;

import static com.phloc.schematron.pure.SchematronResourcePure.fromFile;
import static java.util.UUID.randomUUID;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.taverna.server.master.scape.beanshells.BeanshellSupport.Name;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.phloc.schematron.ISchematronResource;

@Name("AssessMetricsWithSchematron")
public class AssessMetrics extends Support<AssessMetrics> {
	@Input
	private String QLD;
	@Input
	private String metrics;
	@Output
	private List<String> failures = new ArrayList<>();
	@Output
	private List<String> successes = new ArrayList<>();
	@Output
	private boolean isSatisfied;

	private static final String NS = "http://purl.oclc.org/dsdl/svrl";

	@Override
	public void op() throws Exception {
		String id = randomUUID().toString();
		File schematron = new File("schematron_" + id + ".xml");
		File measuresDoc = new File("measures_" + id + ".xml");

		try {
			try (PrintWriter pw = new PrintWriter(schematron, "UTF-8")) {
				pw.println(QLD);
			}
			try {
				try (PrintWriter pw = new PrintWriter(measuresDoc, "UTF-8")) {
					pw.println(metrics);
				}
				matchSchematronToDocument(schematron, new StreamSource(
						measuresDoc));
			} finally {
				measuresDoc.delete();
			}
		} finally {
			schematron.delete();
		}
	}

	private void matchSchematronToDocument(File schematron,
			StreamSource measuresDoc) throws Exception {
		// Construct the validator
		ISchematronResource validator = fromFile(schematron);
		if (!validator.isValidSchematron())
			throw new Exception("invalid schematron document");

		// Perform the validation
		Document result = validator.applySchematronValidation(measuresDoc);

		// Parse the results
		getAssessmentDescriptions(failures, result, "failed-assert",
				"undescribed failure");
		getAssessmentDescriptions(successes, result, "successful-report",
				"undescribed success");
		isSatisfied = failures.isEmpty();
	}

	private void getAssessmentDescriptions(List<String> collector,
			Document result, String container, String defaultMessage) {
		NodeList nl = result.getDocumentElement().getElementsByTagNameNS(NS,
				container);
		for (int i = 0; i < nl.getLength(); i++) {
			Element elem = (Element) nl.item(i);
			NodeList text = elem.getElementsByTagNameNS(NS, "text");
			if (text.getLength() == 0)
				collector.add(defaultMessage);
			else
				collector.add(text.item(0).getTextContent());
		}
	}
}