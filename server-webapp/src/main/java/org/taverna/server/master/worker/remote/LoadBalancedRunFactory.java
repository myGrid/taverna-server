package org.taverna.server.master.worker.remote;

import static java.util.UUID.randomUUID;

import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.UUID;

import javax.xml.bind.JAXBException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.taverna.server.localworker.remote.RemoteRunFactory;
import org.taverna.server.localworker.remote.RemoteSingleRun;
import org.taverna.server.localworker.server.UsageRecordReceiver;
import org.taverna.server.master.ContentsDescriptorBuilder.UriBuilderFactory;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.factories.RunFactory;
import org.taverna.server.master.interaction.InteractionFeedSupport;
import org.taverna.server.master.interfaces.SecurityContextFactory;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.notification.atom.EventDAO;
import org.taverna.server.master.usage.UsageRecordRecorder;
import org.taverna.server.master.utils.UsernamePrincipal;
import org.taverna.server.master.worker.FactoryBean;
import org.taverna.server.master.worker.RemoteRunDelegate;
import org.taverna.server.master.worker.RunDBSupport;

public class LoadBalancedRunFactory implements RunFactory, FactoryBean {
	private Log log = LogFactory
			.getLog("Taverna.Server.Worker.RemoteLoadBalanced");
	private EventDAO masterEventFeed;
	private SecurityContextFactory securityFactory; // TODO
	private InteractionFeedSupport interactionFeedSupport; // TODO
	private UriBuilderFactory baseurifactory; // TODO
	RunDBSupport runDB; // TODO
	TaskExecutor urProcessorPool; // TODO
	UsageRecordRecorder usageRecordSink; // TODO

	@Override
	public TavernaRun create(UsernamePrincipal creator, Workflow workflow)
			throws NoCreateException {
		try {
			Date now = new Date();
			UUID id = issueID();
			RemoteSingleRun rsr = getRealRun(creator, workflow,
					makeURReceiver(creator), id);
			RemoteRunDelegate run = new RemoteRunDelegate(now, workflow, rsr,
					getDefaultLifetime(), runDB, id, this);
			run.setSecurityContext(securityFactory.create(run, creator));
			URL feedUrl = interactionFeedSupport.getFeedURI(run).toURL();
			URL webdavUrl = baseurifactory.getRunUriBuilder(run)
					.path("wd/interactions").build().toURL();
			rsr.setInteractionServiceDetails(feedUrl, webdavUrl);
			return run;
		} catch (NoCreateException e) {
			log.warn("failed to build run instance", e);
			throw e;
		} catch (Exception e) {
			log.warn("failed to build run instance", e);
			throw new NoCreateException("failed to build run instance", e);
		}
	}

	/**
	 * Make a Remote object that can act as a consumer for usage records.
	 * 
	 * @param creator
	 * 
	 * @return The receiver, or <tt>null</tt> if the construction fails.
	 */
	private UsageRecordReceiver makeURReceiver(UsernamePrincipal creator) {
		try {
			@edu.umd.cs.findbugs.annotations.SuppressWarnings({
					"SE_BAD_FIELD_INNER_CLASS", "SE_NO_SERIALVERSIONID" })
			@SuppressWarnings("serial")
			class URReceiver extends UnicastRemoteObject implements
					UsageRecordReceiver {
				public URReceiver() throws RemoteException {
					super();
				}

				@Override
				public void acceptUsageRecord(final String usageRecord) {
					if (usageRecordSink != null && urProcessorPool != null)
						urProcessorPool.execute(new Runnable() {
							@Override
							public void run() {
								usageRecordSink.storeUsageRecord(usageRecord);
							}
						});
					urProcessorPool.execute(new Runnable() {
						@Override
						public void run() {
							runDB.checkForFinishNow();
						}
					});
				}
			}
			return new URReceiver();
		} catch (RemoteException e) {
			log.warn("failed to build usage record receiver", e);
			return null;
		}
	}

	private RemoteSingleRun getRealRun(UsernamePrincipal creator,
			Workflow workflow, UsageRecordReceiver usageRecordReceiver, UUID id)
			throws RemoteException, JAXBException {
		return pickLocation(creator, workflow).make(
				serializeWorkflow(workflow), creator.getName(),
				usageRecordReceiver, id);
	}

	private String serializeWorkflow(Workflow workflow) throws JAXBException {
		return workflow.marshal();
	}

	private UUID issueID() {
		return randomUUID();
	}

	private int getDefaultLifetime() {
		// TODO Auto-generated method stub
		return 0;
	}

	private RemoteRunFactory pickLocation(UsernamePrincipal creator,
			Workflow workflow) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAllowingRunsToStart() {
		// TODO Auto-generated method stub
		return false;
	}

	@Autowired(required = true)
	public void setMasterEventFeed(EventDAO masterEventFeed) {
		this.masterEventFeed = masterEventFeed;
	}

	@Override
	public EventDAO getMasterEventFeed() {
		return masterEventFeed;
	}
}
