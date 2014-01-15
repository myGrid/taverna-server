package org.taverna.server.master;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.taverna.server.master.exceptions.FilesystemAccessException;
import org.taverna.server.master.interfaces.File;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Simple concatenation of files.
 * 
 * @author Donal Fellows
 */
public class FileConcatenation implements Iterable<File> {
	@NonNull
	private final List<File> files = new ArrayList<File>();

	public void add(@NonNull File f) {
		files.add(f);
	}

	public boolean isEmpty() {
		return files.isEmpty();
	}

	/**
	 * @return The total length of the files, or -1 if this cannot be
	 *         determined.
	 */
	public long size() {
		long size = 0;
		for (File f : files)
			try {
				if (f != null)
					size += f.getSize();
			} catch (FilesystemAccessException e) {
				// Ignore; shouldn't happen but can't guarantee
			}
		return (size == 0 && !isEmpty() ? -1 : size);
	}

	/**
	 * Get the concatenated files.
	 * 
	 * @param encoding
	 *            The encoding to use.
	 * @return The concatenated files.
	 * @throws UnsupportedEncodingException
	 *             If the encoding doesn't exist.
	 */
	@NonNull
	public String get(String encoding) throws UnsupportedEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		for (File f : files)
			try {
				if (f != null)
					baos.write(f.getContents(0, -1));
			} catch (FilesystemAccessException e) {
				continue;
			} catch (IOException e) {
				continue;
			}
		return baos.toString(encoding);
	}

	@Override
	@NonNull
	public Iterator<File> iterator() {
		return files.iterator();
	}
}