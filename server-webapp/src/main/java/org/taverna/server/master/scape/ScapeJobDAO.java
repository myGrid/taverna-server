package org.taverna.server.master.scape;

import java.util.List;

import javax.jdo.annotations.PersistenceAware;

import org.taverna.server.master.utils.JDOSupport;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Database access layer that manages which jobs are SCAPE jobs.
 * 
 * @author Donal Fellows
 */
@PersistenceAware
public class ScapeJobDAO extends JDOSupport<ScapeJob> {
	protected ScapeJobDAO() {
		super(ScapeJob.class);
	}

	@NonNull
	@SuppressWarnings("unchecked")
	@WithinSingleTransaction
	public List<String> listNotifiableJobs() {
		return (List<String>) namedQuery("notifiable").execute();
	}

	@WithinSingleTransaction
	public void setScapeJob(@NonNull String id) {
		this.persist(new ScapeJob(id));
	}

	@WithinSingleTransaction
	public boolean isScapeJob(@NonNull String id) {
		Integer count = (Integer) namedQuery("exists").execute(id);
		return count != null && count.intValue() > 0;
	}

	@WithinSingleTransaction
	public void deleteJob(@NonNull String id) {
		delete(getById(id));
	}

	@WithinSingleTransaction
	@Nullable
	public String getNotify(@NonNull String id) {
		ScapeJob job = getById(id);
		return job == null ? null : job.getNotify();
	}

	@WithinSingleTransaction
	public void setNotify(@NonNull String id, @Nullable String notify) {
		ScapeJob job = getById(id);
		if (job != null)
			job.setNotify(notify == null || notify.trim().isEmpty() ? null
					: notify.trim());
	}

	@WithinSingleTransaction
	@Nullable
	public String updateNotify(@NonNull String id, @Nullable String notify) {
		setNotify(id, notify);
		return getNotify(id);
	}
}