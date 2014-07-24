package org.taverna.server.master.scape.beanshells;

import static javax.xml.bind.DatatypeConverter.printDateTime;
import info.lc.xmlns.premis_v2.EventComplexType;
import info.lc.xmlns.premis_v2.EventOutcomeDetailComplexType;
import info.lc.xmlns.premis_v2.EventOutcomeInformationComplexType;
import info.lc.xmlns.premis_v2.ExtensionComplexType;
import info.lc.xmlns.premis_v2.ObjectFactory;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import eu.scape_project.util.ScapeMarshaller;

public class GeneratePremisEvent extends Support<GeneratePremisEvent> {
	@Input
	private List<String> pieces;
	@Input
	private String planID;
	@Output
	private String event;

	@Override
	protected void op() throws Exception {
		StringBuilder sb = new StringBuilder("<planExecutionDetails plan=\"")
				.append(planID).append("\">");
		for (String piece : pieces)
			sb.append(piece);
		sb.append("</planExecutionDetails>");
		Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().newDocument();
		Element p = doc.createElementNS("http://www.w3.org/1999.xhtml", "p");
		p.appendChild(doc.createCDATASection(sb.toString()));

		ObjectFactory factory = new ObjectFactory();
		EventComplexType ev = factory.createEventComplexType();
		ev.setEventType("migration");
		ev.setEventDateTime(printDateTime(Calendar.getInstance()));
		ev.setEventDetail("The plan "
				+ planID
				+ " was applied to a set of representations to generate new representations.");
		EventOutcomeInformationComplexType outcome = factory
				.createEventOutcomeInformationComplexType();
		ev.getEventOutcomeInformation().add(outcome);
		outcome.getContent().add(factory.createEventOutcome("success"));
		EventOutcomeDetailComplexType detail = factory
				.createEventOutcomeDetailComplexType();
		outcome.getContent().add(factory.createEventOutcomeDetail(detail));
		detail.setEventOutcomeDetailNote("detected properties");
		ExtensionComplexType ext = factory.createExtensionComplexType();
		detail.getEventOutcomeDetailExtension().add(ext);
		ext.getAny().add(p);

		// TODO There are other parts of the event in there!

		ScapeMarshaller sm = ScapeMarshaller.newInstance();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(factory.createEvent(ev), baos);
		event = baos.toString();
	}

}
