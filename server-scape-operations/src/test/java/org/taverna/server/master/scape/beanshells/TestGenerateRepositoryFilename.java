package org.taverna.server.master.scape.beanshells;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.junit.Before;
import org.junit.Test;

public class TestGenerateRepositoryFilename {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testNoRealFile() throws Exception {
		GenerateRepositoryFilename op = new GenerateRepositoryFilename();
		op.init("repositoryDirectory", "/foo/bar");
		op.init("temporaryFile", "/tmp/foobarfoobar");
		op.perform();
		assertEquals("/foo/bar/foobarfoobar", op.getResult("repositoryFile"));
	}

	@Test
	public void testRealFile() throws Exception {
		File tmp = new File("/tmp/foobarfoobar");
		assertFalse(tmp.exists());
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
				for (int i = 0; i < 10; i++)
					pw.println("This is some text.");
			}
			assertTrue(tmp.exists());
			GenerateRepositoryFilename op = new GenerateRepositoryFilename();
			op.init("repositoryDirectory", "/foo/bar");
			op.init("temporaryFile", tmp.getAbsolutePath());
			op.perform();
			assertEquals("/foo/bar/foobarfoobar.txt",
					op.getResult("repositoryFile"));
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void testRealFile1() throws Exception {
		File tmp = new File("/tmp/foobarfoobar.");
		assertFalse(tmp.exists());
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
				for (int i = 0; i < 10; i++)
					pw.println("This is some text.");
			}
			assertTrue(tmp.exists());
			GenerateRepositoryFilename op = new GenerateRepositoryFilename();
			op.init("repositoryDirectory", "/foo/bar");
			op.init("temporaryFile", tmp.getAbsolutePath());
			op.perform();
			assertEquals("/foo/bar/foobarfoobar.txt",
					op.getResult("repositoryFile"));
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void testRealFile2() throws Exception {
		File tmp = new File("/tmp/foobarfoobar..");
		assertFalse(tmp.exists());
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
				for (int i = 0; i < 10; i++)
					pw.println("This is some text.");
			}
			assertTrue(tmp.exists());
			GenerateRepositoryFilename op = new GenerateRepositoryFilename();
			op.init("repositoryDirectory", "/foo/bar");
			op.init("temporaryFile", tmp.getAbsolutePath());
			op.perform();
			assertEquals("/foo/bar/foobarfoobar.txt",
					op.getResult("repositoryFile"));
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void testRealFile3() throws Exception {
		File tmp = new File("/tmp/foobarfoobar...");
		assertFalse(tmp.exists());
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
				for (int i = 0; i < 10; i++)
					pw.println("This is some text.");
			}
			assertTrue(tmp.exists());
			GenerateRepositoryFilename op = new GenerateRepositoryFilename();
			op.init("repositoryDirectory", "/foo/bar");
			op.init("temporaryFile", tmp.getAbsolutePath());
			op.perform();
			assertEquals("/foo/bar/foobarfoobar.txt",
					op.getResult("repositoryFile"));
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void testRealFileExt() throws Exception {
		File tmp = new File("/tmp/foobarfoobar.txt");
		assertFalse(tmp.exists());
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
				for (int i = 0; i < 10; i++)
					pw.println("This is some text.");
			}
			assertTrue(tmp.exists());
			GenerateRepositoryFilename op = new GenerateRepositoryFilename();
			op.init("repositoryDirectory", "/foo/bar");
			op.init("temporaryFile", tmp.getAbsolutePath());
			op.perform();
			assertEquals("/foo/bar/foobarfoobar.txt",
					op.getResult("repositoryFile"));
		} finally {
			tmp.delete();
		}
	}
}
