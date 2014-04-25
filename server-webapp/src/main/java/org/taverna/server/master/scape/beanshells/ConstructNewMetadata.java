package org.taverna.server.master.scape.beanshells;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import eu.scape_project.model.Identifier;
import eu.scape_project.model.IntellectualEntity;
import eu.scape_project.model.Representation;
import eu.scape_project.util.ScapeMarshaller;

@Deprecated
class ConstructNewMetadata implements BeanshellSupport {
	static String originalMetadata;
	static String newInformation;
	static String generatedFilename;
	static String creator;
	static String contentType;
	String newMetadata;

	@Override
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