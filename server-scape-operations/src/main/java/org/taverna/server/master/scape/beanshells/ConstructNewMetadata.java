package org.taverna.server.master.scape.beanshells;

import static java.util.UUID.randomUUID;
import static org.taverna.server.master.scape.beanshells.utils.XmlUtils.parseDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import javax.annotation.Nullable;

import org.w3c.dom.Element;

import eu.scape_project.model.File.Builder;
import eu.scape_project.model.Identifier;
import eu.scape_project.model.IntellectualEntity;
import eu.scape_project.model.Representation;
import eu.scape_project.util.ScapeMarshaller;

public class ConstructNewMetadata extends Support<ConstructNewMetadata> {
	@Input
	private String originalMetadata;
	@Input
	private String newInformation;
	@Input
	@Nullable
	private String generatedFilename;
	@Input
	private String creator;
	@Input
	@Nullable
	private String contentType;
	@Output
	private String newMetadata;

	@Override
	public void op() throws Exception {
		ScapeMarshaller sm = ScapeMarshaller.newInstance();
		String id1 = randomUUID().toString();
		String id2 = randomUUID().toString();

		Element newMeta = parseDocument(newInformation).getDocumentElement();

		IntellectualEntity original = sm.deserialize(IntellectualEntity.class,
				new ByteArrayInputStream(originalMetadata.getBytes()));

		Representation.Builder rep = new Representation.Builder()
				.identifier(new Identifier(id2)).technical(newMeta)
				.title("output from " + creator);
		if (generatedFilename != null) {
			File fileHandle = new File(generatedFilename);
			Builder file = new Builder().identifier(new Identifier(id1))
					.uri(fileHandle.toURI()).filename(fileHandle.getName());
			if (contentType != null)
				file.mimetype(contentType);
			rep.file(file.build());
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(
				new IntellectualEntity.Builder(original).representation(
						rep.build()).build(), baos);
		newMetadata = baos.toString();
	}
}