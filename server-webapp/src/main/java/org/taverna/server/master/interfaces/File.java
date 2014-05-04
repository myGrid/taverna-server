/*
 * Copyright (C) 2010-2011 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.interfaces;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.taverna.server.master.exceptions.FilesystemAccessException;

/**
 * Represents a file in the working directory of a workflow instance run, or in
 * some sub-directory of it.
 * 
 * @author Donal Fellows
 * @see Directory
 */
public interface File extends DirectoryEntry {
	/**
	 * @param offset
	 *            Where in the file to start reading.
	 * @param length
	 *            The length of file to read, or -1 to read to the end of the
	 *            file.
	 * @return The literal byte contents of the section of the file, or null if
	 *         the section doesn't exist.
	 * @throws FilesystemAccessException
	 *             If the read of the file goes wrong.
	 */
	@Nullable
	byte[] getContents(int offset, int length) throws FilesystemAccessException;

	/**
	 * Write the data to the file, totally replacing what was there before.
	 * 
	 * @param data
	 *            The literal bytes that will form the new contents of the file.
	 * @throws FilesystemAccessException
	 *             If the write to the file goes wrong.
	 */
	void setContents(@Nonnull byte[] data) throws FilesystemAccessException;

	/**
	 * Append the data to the file.
	 * 
	 * @param data
	 *            The literal bytes that will be added on to the end of the
	 *            file.
	 * @throws FilesystemAccessException
	 *             If the write to the file goes wrong.
	 */
	void appendContents(@Nonnull byte[] data) throws FilesystemAccessException;

	/**
	 * @return The length of the file, in bytes.
	 * @throws FilesystemAccessException
	 *             If the read of the file size goes wrong.
	 */
	long getSize() throws FilesystemAccessException;

	/**
	 * Asks for the argument file to be copied to this one.
	 * 
	 * @param from
	 *            The source file.
	 * @throws FilesystemAccessException
	 *             If anything goes wrong.
	 */
	void copy(@Nonnull File from) throws FilesystemAccessException;
}
