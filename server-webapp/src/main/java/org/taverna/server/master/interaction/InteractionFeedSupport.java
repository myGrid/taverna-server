/*
 * Copyright (C) 2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.interaction;

import static java.util.Collections.reverse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.abdera.Abdera;
import org.apache.abdera.factory.Factory;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.writer.Writer;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.TavernaServerSupport;
import org.taverna.server.master.exceptions.FilesystemAccessException;
import org.taverna.server.master.exceptions.NoDirectoryEntryException;
import org.taverna.server.master.exceptions.NoUpdateException;
import org.taverna.server.master.interfaces.Directory;
import org.taverna.server.master.interfaces.DirectoryEntry;
import org.taverna.server.master.interfaces.File;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.UriBuilderFactory;
import org.taverna.server.master.utils.FilenameUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Bean that supports interaction feeds. This glues together the Abdera
 * serialization engine and the directory-based model used inside the server.
 * 
 * @author Donal Fellows
 */
public class InteractionFeedSupport {
	/**
	 * The name of the resource within the run resource that is the run's
	 * interaction feed resource.
	 */
	public static final String FEED_URL_DIR = "interaction";
	/**
	 * The name of the directory below the run working directory that will
	 * contain the entries of the interaction feed.
	 */
	public static final String FEED_DIR = "feed";
	/**
	 * Should the contents of the entry be stripped when describing the overall
	 * feed? This makes sense if (and only if) large entries are being pushed
	 * through the feed.
	 */
	private static final boolean STRIP_CONTENTS = false;
	/** Maximum size of an entry before truncation. */
	private static final long MAX_ENTRY_SIZE = 50 * 1024;
	/** Extension for entry files. */
	private static final String EXT = ".atom";

	private TavernaServerSupport support;
	private FilenameUtils utils;
	private Writer writer;
	private Parser parser;
	private Factory factory;
	private UriBuilderFactory uriBuilder;

	private AtomicInteger counter = new AtomicInteger();

	@Required
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	@Required
	public void setUtils(FilenameUtils utils) {
		this.utils = utils;
	}

	@Required
	public void setAbdera(Abdera abdera) {
		this.factory = abdera.getFactory();
		this.parser = abdera.getParser();
		this.writer = abdera.getWriterFactory().getWriter("prettyxml");
	}

	@Required
	// webapp
	public void setUriBuilder(UriBuilderFactory uriBuilder) {
		this.uriBuilder = uriBuilder;
	}

	/**
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @return The URI of the feed
	 */
	@NonNull
	@SuppressWarnings("null")
	public URI getFeedURI(@NonNull TavernaRun run) {
		return uriBuilder.getRunUriBuilder(run).path(FEED_URL_DIR).build();
	}

	/**
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @param id
	 *            The ID of the entry.
	 * @return The URI of the entry.
	 */
	@NonNull
	@SuppressWarnings("null")
	public URI getEntryURI(@NonNull TavernaRun run, @NonNull String id) {
		return uriBuilder.getRunUriBuilder(run)
				.path(FEED_URL_DIR + "/{entryID}").build(id);
	}

	@NonNull
	@SuppressWarnings("null")
	private Entry getEntryFromFile(@NonNull File f)
			throws FilesystemAccessException {
		long size = f.getSize();
		if (size > MAX_ENTRY_SIZE)
			throw new FilesystemAccessException("entry larger than 50kB");
		byte[] contents = f.getContents(0, (int) size);
		Document<Entry> doc = parser.parse(new ByteArrayInputStream(contents));
		return doc.getRoot();
	}

	@SuppressWarnings("null")
	private void putEntryInFile(@NonNull Directory dir, @NonNull String name,
			@NonNull Entry contents) throws FilesystemAccessException,
			NoUpdateException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			writer.writeTo(contents, baos);
		} catch (IOException e) {
			throw new NoUpdateException("failed to serialize the ATOM entry", e);
		}
		File f = dir.makeEmptyFile(support.getPrincipal(), name);
		f.appendContents(baos.toByteArray());
	}

	@NonNull
	private List<DirectoryEntry> listPossibleEntries(TavernaRun run)
			throws FilesystemAccessException, NoDirectoryEntryException {
		@SuppressWarnings("null")
		List<DirectoryEntry> entries = new ArrayList<DirectoryEntry>(utils
				.getDirectory(run, FEED_DIR).getContentsByDate());
		reverse(entries);
		return entries;
	}

	@NonNull
	@SuppressWarnings("null")
	private String getRunURL(TavernaRun run) {
		return new IRI(uriBuilder.getRunUriBuilder(run).build()).toString();
	}

	/**
	 * Get the interaction feed for a partciular run.
	 * 
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @return The Abdera feed descriptor.
	 * @throws FilesystemAccessException
	 *             If the feed directory can't be read for some reason.
	 * @throws NoDirectoryEntryException
	 *             If the feed directory doesn't exist or an entry is
	 *             unexpectedly removed.
	 */
	public Feed getRunFeed(@NonNull TavernaRun run)
			throws FilesystemAccessException, NoDirectoryEntryException {
		URI feedURI = getFeedURI(run);
		Feed feed = factory.newFeed();
		feed.setTitle("Interactions for Taverna Run \"" + run.getName() + "\"");
		feed.addLink(new IRI(feedURI).toString(), "self");
		feed.addLink(getRunURL(run), "workflowrun");
		boolean fetchedDate = false;
		for (DirectoryEntry de : listPossibleEntries(run)) {
			if (!(de instanceof File))
				continue;
			try {
				@NonNull
				Entry e = getEntryFromFile((File) de);
				if (STRIP_CONTENTS)
					e.setContentElement(null);
				feed.addEntry(e);
				if (!fetchedDate) {
					Date last = e.getUpdated();
					if (last == null)
						last = e.getPublished();
					if (last == null)
						last = de.getModificationDate();
					feed.setUpdated(last);
					fetchedDate = true;
				}
			} catch (FilesystemAccessException e) {
				// Can't do anything about it, so we'll just drop the entry.
			}
		}
		return feed;
	}

	/**
	 * Gets the contents of a particular feed entry.
	 * 
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @param entryID
	 *            The identifier (from the path) of the entry to read.
	 * @return The description of the entry.
	 * @throws FilesystemAccessException
	 *             If the entry can't be read or is too large.
	 * @throws NoDirectoryEntryException
	 *             If the entry can't be found.
	 */
	@NonNull
	public Entry getRunFeedEntry(@NonNull TavernaRun run,
			@NonNull String entryID) throws FilesystemAccessException,
			NoDirectoryEntryException {
		File entryFile = utils.getFile(run, FEED_DIR + "/" + entryID + EXT);
		return getEntryFromFile(entryFile);
	}

	/**
	 * Given a partial feed entry, store a complete feed entry in the filesystem
	 * for a particular run. Note that this does not permit update of an
	 * existing entry; the entry is always created new.
	 * 
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @param entry
	 *            The partial entry to store
	 * @return A link to the entry.
	 * @throws FilesystemAccessException
	 *             If the entry can't be stored.
	 * @throws NoDirectoryEntryException
	 *             If the run is improperly configured.
	 * @throws NoUpdateException
	 *             If the user isn't allowed to do the write.
	 * @throws MalformedURLException
	 *             If a generated URL is illegal (shouldn't happen).
	 */
	@NonNull
	@SuppressWarnings("null")
	public Entry addRunFeedEntry(@NonNull TavernaRun run, @NonNull Entry entry)
			throws FilesystemAccessException, NoDirectoryEntryException,
			NoUpdateException {
		support.permitUpdate(run);
		Date now = new Date();
		entry.newId();
		String localId = "entry_" + counter.incrementAndGet();
		IRI selfLink = new IRI(getEntryURI(run, localId));
		entry.addLink(selfLink.toString(), "self");
		entry.addLink(getRunURL(run), "workflowrun");
		entry.setUpdated(now);
		entry.setPublished(now);
		putEntryInFile(utils.getDirectory(run, FEED_DIR), localId + EXT, entry);
		return getEntryFromFile(utils.getFile(run, FEED_DIR + "/" + localId
				+ EXT));
	}

	/**
	 * Deletes an entry from a feed.
	 * 
	 * @param run
	 *            The workflow run that defines which feed we are operating on.
	 * @param entryID
	 *            The ID of the entry to delete.
	 * @throws FilesystemAccessException
	 *             If the entry can't be deleted
	 * @throws NoDirectoryEntryException
	 *             If the entry can't be found.
	 * @throws NoUpdateException
	 *             If the current user is not permitted to modify the run's
	 *             characteristics.
	 */
	public void removeRunFeedEntry(@NonNull TavernaRun run,
			@NonNull String entryID) throws FilesystemAccessException,
			NoDirectoryEntryException, NoUpdateException {
		support.permitUpdate(run);
		utils.getFile(run, FEED_DIR + "/" + entryID + EXT).destroy();
	}
}
