package org.taverna.server.master.scape.beanshells;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.phloc.schematron.ISchematronResource;
import com.phloc.schematron.pure.SchematronResourcePure;

@Deprecated
@SuppressWarnings({ "rawtypes", "unchecked" })
class AssessMetrics implements BeanshellSupport {
	static String QLD;
	static String metrics;
	List<String> failures;
	String isSatisfied;

	@Override
	public void shell() throws Exception {
		String id = UUID.randomUUID().toString();
		File schematron = new File("schematron_" + id + ".xml");
		File measuresDoc = new File("measures_" + id + ".xml");

		PrintWriter pw = new PrintWriter(schematron, "UTF-8");
		pw.println(QLD);
		pw.close();
		pw = new PrintWriter(measuresDoc, "UTF-8");
		pw.println(metrics);
		pw.close();
		failures = new ArrayList();

		try {
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
		} finally {
			measuresDoc.delete();
			schematron.delete();
		}
	}
}