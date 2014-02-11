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
	public void setScapeJob(@NonNull String jobId, @NonNull String planId) {
		this.persist(new ScapeJob(jobId, planId));
	}

	@WithinSingleTransaction
	public boolean isScapeJob(@NonNull String id) {
		Integer count = (Integer) namedQuery("exists").execute(id);
		return count != null && count.intValue() > 0;
	}

	@WithinSingleTransaction
	public void deleteJob(@NonNull String jobId) {
		delete(getById(jobId));
	}

	@WithinSingleTransaction
	@Nullable
	public String getPlanId(@NonNull String jobId) {
		ScapeJob job = getById(jobId);
		return job == null ? null : job.getPlanId();
	}

	@WithinSingleTransaction
	public int getNotify(@NonNull String jobId) {
		ScapeJob job = getById(jobId);
		return job == null ? 0 : job.getNotify();
	}

	@WithinSingleTransaction
	public void setNotify(@NonNull String jobId, @Nullable String notify) {
		int n = (notify == null ? 0 : Integer.parseInt(notify.trim()));
		ScapeJob job = getById(jobId);
		if (job != null)
			job.setNotify(n == 0 ? 0 : 1);
	}

	@WithinSingleTransaction
	public int updateNotify(@NonNull String jobId, @Nullable String notify) {
		setNotify(jobId, notify);
		return getNotify(jobId);
	}

	@WithinSingleTransaction
	@Nullable
	public ScapeJob getJobRecord(@NonNull String jobId) {
		ScapeJob job = getById(jobId);
		if (job != null)
			job = detach(job);
		return job;
	}
}