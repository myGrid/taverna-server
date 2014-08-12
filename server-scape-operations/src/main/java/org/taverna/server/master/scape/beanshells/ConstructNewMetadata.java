package org.taverna.server.master.scape.beanshells;

import static java.util.UUID.randomUUID;
import static javax.xml.bind.DatatypeConverter.printDateTime;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.makeNewDocument;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.parseDocument;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.serializeDocument;
import info.lc.xmlns.premis_v2.EventComplexType;
import info.lc.xmlns.premis_v2.EventOutcomeDetailComplexType;
import info.lc.xmlns.premis_v2.EventOutcomeInformationComplexType;
import info.lc.xmlns.premis_v2.ExtensionComplexType;
import info.lc.xmlns.premis_v2.ObjectFactory;
import info.lc.xmlns.premis_v2.PremisComplexType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import eu.scape_project.model.File.Builder;
import eu.scape_project.model.Identifier;
import eu.scape_project.model.Representation;
import eu.scape_project.util.ScapeMarshaller;

public class ConstructNewMetadata extends Support<ConstructNewMetadata> {
	@Input
	private String originalMetadata;
	@Input
	private List<String> originalFileID;
	@Input
	private List<String> newInformation;
	@Input(required = false)
	private List<String> generatedFileUri;
	@Input
	private String creator;
	@Input(required = false)
	private List<String> contentType;
	@Input
	private String planID;
	@Output
	private String newMetadata;
	@Output
	private String newRepresentationId;

	private final ScapeMarshaller sm;
	private final ObjectFactory factory;
	private final Document doc;

	public ConstructNewMetadata() throws JAXBException,
			ParserConfigurationException {
		sm = ScapeMarshaller.newInstance();
		factory = new ObjectFactory();
		doc = makeNewDocument();
	}

	@Override
	public void op() throws Exception {
		newRepresentationId = randomUUID().toString();

		Representation original = sm.deserialize(Representation.class,
				new ByteArrayInputStream(originalMetadata.getBytes()));

		Map<String, Integer> fileindexmap = new HashMap<>();
		int idx = 0;
		for (eu.scape_project.model.File f : original.getFiles())
			fileindexmap.put(f.getIdentifier().getValue(), idx++);

		Representation.Builder rep = new Representation.Builder(original)
				.identifier(new Identifier(newRepresentationId))
				.title("representation generated by " + creator);
		StringBuilder overallFileInfo = new StringBuilder(
				"<planExecutionDetails plan=\"").append(planID).append("\">");
		if (generatedFileUri != null) {
			List<eu.scape_project.model.File> updatedFiles = new ArrayList<>(
					original.getFiles());
			Iterator<String> newMeta = newInformation.iterator();
			Iterator<String> origFile = originalFileID.iterator();
			Iterator<String> ct = (contentType == null ? null : contentType
					.iterator());
			for (String fileUri : generatedFileUri)
				processOneFileEntry(newMeta.next(), origFile.next(),
						fileindexmap, overallFileInfo,
						ct == null ? null : ct.next(), updatedFiles, fileUri);
			rep.files(updatedFiles);
		}
		overallFileInfo.append("</planExecutionDetails>");
		rep.provenance(generatePremisEvent(overallFileInfo.toString()));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(rep.build(), baos);
		newMetadata = baos.toString();
	}

	private JAXBElement<?> generatePremisEvent(String details) {
		Element p = doc.createElementNS("http://www.w3.org/1999.xhtml", "p");
		p.appendChild(doc.createCDATASection(details));

		EventComplexType ev = factory.createEventComplexType();
		ev.setEventType("migration");
		ev.setEventDateTime(printDateTime(Calendar.getInstance()));
		ev.setEventDetail("The plan " + planID
				+ " was applied to generate a new representation.");
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

		PremisComplexType premis = factory.createPremisComplexType();
		premis.getEvent().add(ev);
		return factory.createPremis(premis);
	}

	private void processOneFileEntry(String newInformation,
			String originalFileID, Map<String, Integer> fileindexmap,
			StringBuilder overallFileInfo, String contentType,
			List<eu.scape_project.model.File> updatedFiles,
			String generatedFileUri) throws ParserConfigurationException,
			SAXException, IOException, TransformerFactoryConfigurationError,
			TransformerException {
		String id = originalFileID.replaceFirst("^.*/", "");
		Integer idx = fileindexmap.get(id);
		if (idx == null)
			id = randomUUID().toString();
		Element m = parseDocument(newInformation).getDocumentElement();

		Element fileInfo = doc.createElement("file");
		fileInfo.setAttribute("id", id);
		NodeList nl = m.getElementsByTagNameNS(
				"http://ns.taverna.org.uk/2014/scape", "measure");
		for (int i = 0; i < nl.getLength(); i++) {
			Element measure = (Element) nl.item(i);
			Element qa = doc.createElement("qa");
			qa.setAttribute("property", measure.getAttribute("type"));
			qa.setTextContent(measure.getTextContent());
			fileInfo.appendChild(qa);
		}
		overallFileInfo.append(serializeDocument(fileInfo));

		Builder file = new Builder().identifier(new Identifier(id))
				.uri(URI.create(generatedFileUri))
				//.filename(fileHandle.getName())
				.technical(m);
		if (contentType != null)
			file.mimetype(contentType);
		if (idx == null)
			updatedFiles.add(file.build());
		else
			updatedFiles.set(idx, file.build());
	}
}
