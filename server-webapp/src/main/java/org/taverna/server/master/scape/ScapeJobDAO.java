package org.taverna.server.master.scape;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jdo.annotations.PersistenceAware;

import org.taverna.server.master.utils.JDOSupport;

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

	@Nonnull
	@SuppressWarnings("unchecked")
	@WithinSingleTransaction
	public List<String> listNotifiableJobs() {
		return (List<String>) namedQuery("notifiable").execute();
	}

	@WithinSingleTransaction
	public void setScapeJob(@Nonnull String jobId, @Nonnull String planId) {
		this.persist(new ScapeJob(jobId, planId));
	}

	@WithinSingleTransaction
	public boolean isScapeJob(@Nonnull String id) {
		Integer count = (Integer) namedQuery("exists").execute(id);
		return count != null && count.intValue() > 0;
	}

	@WithinSingleTransaction
	public void deleteJob(@Nonnull String jobId) {
		delete(getById(jobId));
	}

	@WithinSingleTransaction
	@Nullable
	public String getPlanId(@Nonnull String jobId) {
		ScapeJob job = getById(jobId);
		return job == null ? null : job.getPlanId();
	}

	@WithinSingleTransaction
	public int getNotify(@Nonnull String jobId) {
		ScapeJob job = getById(jobId);
		return job == null ? 0 : job.getNotify();
	}

	@WithinSingleTransaction
	public void setNotify(@Nonnull String jobId, @Nullable String notify) {
		int n = (notify == null ? 0 : Integer.parseInt(notify.trim()));
		ScapeJob job = getById(jobId);
		if (job != null)
			job.setNotify(n == 0 ? 0 : 1);
	}

	@WithinSingleTransaction
	public int updateNotify(@Nonnull String jobId, @Nullable String notify) {
		setNotify(jobId, notify);
		return getNotify(jobId);
	}

	@WithinSingleTransaction
	@Nullable
	public ScapeJob getJobRecord(@Nonnull String jobId) {
		ScapeJob job = getById(jobId);
		if (job != null)
			job = detach(job);
		return job;
	}
}