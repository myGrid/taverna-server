package org.taverna.server.master.scape;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;

/**
 * The representation of a SCAPE job in the database.
 * 
 * @author Donal Fellows
 */
@SuppressWarnings("serial")
@PersistenceCapable(schema = ScapeJob.SCHEMA, table = ScapeJob.TABLE)
@Queries({
		@Query(name = "exists", language = "SQL", value = ScapeJob.EXISTS_QUERY, unique = "true", resultClass = Integer.class),
		@Query(name = "notifiable", language = "SQL", value = ScapeJob.NOTIFY_QUERY, unique = "false", resultClass = String.class), })
class ScapeJob implements Serializable {
	static final String SCHEMA = "SCAPE";
	static final String TABLE = "JOB";
	private static final String FULL_NAME = SCHEMA + "." + TABLE;
	static final String EXISTS_QUERY = "SELECT count(*) FROM " + FULL_NAME
			+ " WHERE id = ?";
	static final String NOTIFY_QUERY = "SELECT id FROM " + FULL_NAME
			+ " WHERE notify = 1";

	@Persistent(primaryKey = "true")
	@Column(length = 48)
	private String id;
	@Persistent
	private int notify;
	@Persistent
	private String planId;

	public ScapeJob() {
	}

	public ScapeJob(@Nonnull String id) {
		this.id = id;
	}

	public ScapeJob(@Nonnull String id, @Nonnull String planId) {
		this.id = id;
		this.planId = planId;
		this.notify = 1;
	}

	@Nonnull
	public String getJobId() {
		return id;
	}

	@Nonnull
	public String getPlanId() {
		return id;
	}

	public int getNotify() {
		return notify;
	}

	public void setNotify(int notify) {
		this.notify = notify;
	}
}