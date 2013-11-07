package org.taverna.server.master;

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.common.Status.Finished;
import static org.taverna.server.master.rest.scape.PreservationActionPlan.ExecutablePlan.ExecutablePlanType.Taverna2;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.taverna.server.master.common.Uri;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.interfaces.Policy;
import org.taverna.server.master.interfaces.RunStore;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.scape.PreservationActionPlan;
import org.taverna.server.master.rest.scape.PreservationActionPlan.DigitalObject;
import org.taverna.server.master.rest.scape.ScapeExecutionService;
import org.taverna.server.master.scape.SplicingEngine;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

@Path("/")
public class ScapeExecutor implements ScapeExecutionService {
	@Autowired
	private TavernaServerSupport support;
	@Autowired
	private RunStore runStore;
	@Autowired
	private Policy policy;
	@Autowired
	private SplicingEngine splicer;

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Jobs listJobs(@NonNull UriInfo ui) {
		@NonNull
		Jobs jobs = new Jobs();
		for (@NonNull
		Entry<String, TavernaRun> entry : runStore.listRuns(
				support.getPrincipal(), policy).entrySet())
			if (isScapeRun(entry.getValue()))
				jobs.job.add(new Uri(ui, "{id}", entry.getKey()));
		return jobs;
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Response startJob(@NonNull PreservationActionPlan plan,
			@NonNull UriInfo ui) throws NoCreateException {
		@NonNull
		List<DigitalObject> objs = plan.object;
		if (objs.size() == 0)
			return status(400).entity("no objects to act on").build();
		if (plan.executablePlan.type != Taverna2)
			return status(400).entity("that type of plan not supported")
					.build();
		@NonNull
		String id;
		try {
			id = submitAndStart(
					splicer.constructWorkflow(
							plan.executablePlan.workflowDocument,
							plan.qualityLevelDescription.schematronDocument != null),
					objs, plan.qualityLevelDescription.schematronDocument);
		} catch (IOException e) {
			throw new NoCreateException("failed to construct workflow", e);
		} catch (ParserConfigurationException e) {
			throw new NoCreateException("failed to construct workflow", e);
		} catch (SAXException e) {
			throw new NoCreateException("failed to construct workflow", e);
		}
		return created(ui.getRequestUriBuilder().path("{id}").build(id))
				.build();
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Job getStatus(@NonNull String id, @NonNull UriInfo ui)
			throws UnknownRunException {
		@NonNull
		TavernaRun r = runStore.getRun(support.getPrincipal(), policy, id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		Job job = new Job();
		job.status = r.getStatus();
		job.serverJob = new Uri(ui.getBaseUriBuilder(), "rest/runs/{id}", id);
		// TODO Consider doing output by a special URL
		if (job.status == Finished)
			job.output = new Uri(ui.getBaseUriBuilder(),
					"rest/runs/{id}/wd/out", id);
		return job;
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Response deleteJob(@NonNull String id) throws UnknownRunException,
			NoDestroyException {
		TavernaRun r = runStore.getRun(support.getPrincipal(), policy, id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		support.unregisterRun(id, null);
		return Response.noContent().build();
	}

	private boolean isScapeRun(@NonNull TavernaRun run) {
		// TODO: Filtering by what API created the job
		return true;
	}

	private String submitAndStart(@NonNull Workflow w,
			@NonNull List<DigitalObject> objs, @Nullable Element schematron)
			throws NoCreateException {
		// FIXME implement this!
		throw new NoCreateException("operation not yet implemented");
	}
}
