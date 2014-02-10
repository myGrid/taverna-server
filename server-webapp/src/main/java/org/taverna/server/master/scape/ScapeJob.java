package org.taverna.server.master.scape;

import java.io.Serializable;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.Queries;
import javax.jdo.annotations.Query;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
			+ " WHERE notify IS NOT NULL";

	@Persistent(primaryKey = "true")
	@Column(length = 48)
	private String id;
	@Persistent
	private String notify;
	@Persistent
	private String planId;

	public ScapeJob() {
	}

	public ScapeJob(@NonNull String id) {
		this.id = id;
	}

	public ScapeJob(@NonNull String id, @NonNull String planId) {
		this.id = id;
		this.planId = planId;
	}

	public ScapeJob(@NonNull String id, @NonNull String planId, @NonNull String notify) {
		this.id = id;
		this.planId = planId;
		this.notify = notify;
	}

	@NonNull
	public String getJobId() {
		return id;
	}

	@NonNull
	public String getPlanId() {
		return id;
	}

	@Nullable
	public String getNotify() {
		return notify;
	}

	public void setNotify(@Nullable String notify) {
		this.notify = notify;
	}
}