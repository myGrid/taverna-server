package org.taverna.server.master.scape.beanshells;

import static java.util.UUID.randomUUID;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.parseDocument;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.serializeDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
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
	private List<String> generatedFilename;
	@Input
	private String creator;
	@Input(required = false)
	private List<String> contentType;
	@Output
	private String newMetadata;
	@Output
	private String payloadForPremisEvent;

	@Override
	public void op() throws Exception {
		ScapeMarshaller sm = ScapeMarshaller.newInstance();
		String id2 = randomUUID().toString();

		Representation original = sm.deserialize(Representation.class,
				new ByteArrayInputStream(originalMetadata.getBytes()));

		Map<String, Integer> fileindexmap = new HashMap<>();
		int idx = 0;
		for (eu.scape_project.model.File f : original.getFiles())
			fileindexmap.put(f.getIdentifier().getValue(), idx++);

		Representation.Builder rep = new Representation.Builder(original)
				.identifier(new Identifier(id2))
				.title("output from " + creator);
		StringBuilder overallFileInfo = new StringBuilder();
		if (generatedFilename != null) {
			List<eu.scape_project.model.File> updatedFiles = new ArrayList<>(
					original.getFiles());
			Iterator<String> newMeta = newInformation.iterator();
			Iterator<String> origFile = originalFileID.iterator();
			Iterator<String> ct = (contentType == null ? null : contentType
					.iterator());
			for (String f : generatedFilename)
				processOneFileEntry(newMeta.next(), origFile.next(),
						fileindexmap, overallFileInfo,
						ct == null ? null : ct.next(), updatedFiles, f);
			rep.files(updatedFiles);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(rep.build(), baos);
		newMetadata = baos.toString();
		payloadForPremisEvent = overallFileInfo.toString();
	}

	private void processOneFileEntry(String newInformation,
			String originalFileID, Map<String, Integer> fileindexmap,
			StringBuilder overallFileInfo, String contentType,
			List<eu.scape_project.model.File> updatedFiles,
			String generatedFilename) throws ParserConfigurationException,
			SAXException, IOException, TransformerFactoryConfigurationError,
			TransformerException {
		String newID = randomUUID().toString();
		Element m = parseDocument(newInformation).getDocumentElement();
		File fileHandle = new File(generatedFilename);

		Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().newDocument();
		Element fileInfo = doc.createElement("file");
		fileInfo.setAttribute("id", newID);
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

		Builder file = new Builder().identifier(new Identifier(newID))
				.uri(fileHandle.toURI()).filename(fileHandle.getName())
				.technical(m);
		if (contentType != null)
			file.mimetype(contentType);
		Integer i = fileindexmap.get(originalFileID);
		if (i == null)
			updatedFiles.add(file.build());
		else
			updatedFiles.set(i, file.build());
	}
}