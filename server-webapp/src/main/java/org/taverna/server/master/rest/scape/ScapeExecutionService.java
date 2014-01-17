package org.taverna.server.master.rest.scape;

import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.rest.ContentTypes.TEXT;
import static org.taverna.server.master.rest.ContentTypes.XML;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.cxf.jaxrs.model.wadl.Description;
import org.taverna.server.master.common.Status;
import org.taverna.server.master.common.Uri;
import org.taverna.server.master.common.version.Version;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.NoUpdateException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.rest.ContentTypes;

import edu.umd.cs.findbugs.annotations.NonNull;
import at.ac.tuwien.ifs.dp.plato.PreservationActionPlan;

@RolesAllowed(USER)
@Description("This is SCAPE service interface sitting on top of Taverna "
		+ Version.JAVA + " Server.")
public interface ScapeExecutionService {
	@GET
	@Path("/")
	@Produces(XML)
	@RolesAllowed(USER)
	Jobs listJobs(@NonNull @Context UriInfo ui);

	@POST
	@Path("/")
	@Consumes(XML)
	@RolesAllowed(USER)
	Response startJob(@NonNull PreservationActionPlan plan,
			@Context @NonNull UriInfo ui) throws NoCreateException;

	@OPTIONS
	@Path("/")
	Response rootOpt();

	@GET
	@Path("{id}")
	@Produces(XML)
	Job getStatus(@NonNull @PathParam("id") String id,
			@Context @NonNull UriInfo ui) throws UnknownRunException;

	@DELETE
	@Path("{id}")
	Response deleteJob(@NonNull @PathParam("id") String id)
			throws UnknownRunException, NoDestroyException;

	@OPTIONS
	@Path("{id}")
	Response jobOpt(@NonNull @PathParam("id") String id)
			throws UnknownRunException;

	@GET
	@Path("{id}/notification")
	@Produces(TEXT)
	String getNotification(@NonNull @PathParam("id") String id)
			throws UnknownRunException;

	@PUT
	@Path("{id}/notification")
	@Consumes(TEXT)
	@Produces(TEXT)
	String setNotification(@NonNull @PathParam("id") String id, String newValue)
			throws UnknownRunException, NoUpdateException;

	@OPTIONS
	@Path("{id}/notification")
	Response notifyOpt(@NonNull @PathParam("id") String id)
			throws UnknownRunException;

	@XmlRootElement(name = "jobs")
	public static class Jobs {
		@XmlElement
		public List<Uri> job;
	}

	@XmlRootElement(name = "job")
	public static class Job {
		@XmlElement(required = true)
		public Status status;
		@XmlElement
		public Uri serverJob;
		@XmlElement
		public Uri output;
		@XmlElement
		public Uri enactedWorkflow;
		@XmlElement
		public Uri notificationAddress;
	}
}
