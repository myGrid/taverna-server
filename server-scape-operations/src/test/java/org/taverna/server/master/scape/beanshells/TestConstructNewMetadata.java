package org.taverna.server.master.scape.beanshells;

import static java.nio.file.Files.readAllBytes;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

public class TestConstructNewMetadata {
	private String originalMetadata;
	private String newInformation;
	private String createdMetadata;

	@Before
	public void loadData() throws Exception {
		originalMetadata = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_original.xml").toURI())));
		newInformation = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_newinfo.xml").toURI())));
		createdMetadata = new String(readAllBytes(Paths.get(getClass()
				.getResource("/example_out.xml").toURI())));
	}

	private static final Pattern UUID = Pattern
			.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private String mapUUIDs(String s) {
		Matcher m = UUID.matcher(s);
		StringBuffer buffer = new StringBuffer();
		Map<String, Integer> map = new HashMap<>();
		while (m.find()) {
			Integer i = map.get(m.group());
			if (i == null)
				map.put(m.group(), i = map.size());
			m.appendReplacement(buffer, "UUID-" + i);
		}
		return m.appendTail(buffer).toString();
	}

	@Test
	public void test() throws Exception {
		ConstructNewMetadata op = new ConstructNewMetadata()
				.init("contentType", asList("text/plain"))
				.init("creator", getClass().toString())
				.init("generatedFilename", asList("/tmp/example/BCD.txt"))
				.init("originalMetadata", originalMetadata)
				.init("originalFileID", asList("F9"))
				.init("newInformation", asList(newInformation));
		op.perform();
		assertEquals(mapUUIDs(createdMetadata), mapUUIDs(op.getResult("newMetadata").toString()));
	}

}
