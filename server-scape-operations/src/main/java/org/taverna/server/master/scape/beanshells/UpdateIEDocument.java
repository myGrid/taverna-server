package org.taverna.server.master.scape.beanshells;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.bind.JAXBException;

import org.taverna.server.master.scape.beanshells.BeanshellSupport.Name;

import eu.scape_project.model.IntellectualEntity;
import eu.scape_project.model.Representation;
import eu.scape_project.util.ScapeMarshaller;

@Name("UpdateIntellectualEntityDocument")
public class UpdateIEDocument extends Support<UpdateIEDocument> {
	@Input
	private String originalIE;
	@Input
	private String representation;
	@Output
	private String updatedIE;

	private final ScapeMarshaller sm;

	public UpdateIEDocument() throws JAXBException {
		sm = ScapeMarshaller.newInstance();
	}

	@Override
	protected void op() throws Exception {
		Representation r = sm.deserialize(Representation.class,
				new ByteArrayInputStream(representation.getBytes("UTF-8")));
		IntellectualEntity ie = sm.deserialize(IntellectualEntity.class,
				new ByteArrayInputStream(originalIE.getBytes("UTF-8")));
		ie = new IntellectualEntity.Builder(ie).representation(r).build();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sm.serialize(ie, baos);
		updatedIE = baos.toString("UTF-8");
	}
}
