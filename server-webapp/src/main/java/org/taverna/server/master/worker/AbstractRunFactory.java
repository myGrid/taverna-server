package org.taverna.server.master.worker;

import static java.util.UUID.randomUUID;

import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.logging.Log;
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

public abstract class AbstractRunFactory implements RunFactory, FactoryBean {
	protected abstract Log log();

	private EventDAO masterEventFeed;
	private SecurityContextFactory securityFactory; // TODO
	private InteractionFeedSupport interactionFeedSupport; // TODO
	private UriBuilderFactory baseurifactory; // TODO
	RunDBSupport runDB; // TODO
	TaskExecutor urProcessorPool; // TODO
	UsageRecordRecorder usageRecordSink; // TODO

	@Override
	public final TavernaRun create(UsernamePrincipal creator, Workflow workflow)
			throws NoCreateException {
		try {
			Date now = new Date();
			RemoteRunFactory realFactory = pickLocation(creator, workflow);
			UUID id = randomUUID();
			RemoteSingleRun rsr = realFactory.make(workflow.marshal(),
					creator.getName(), makeURReceiver(creator), id);
			RemoteRunDelegate run = new RemoteRunDelegate(now, workflow, rsr,
					getDefaultLifetime(), runDB, id, this);
			run.setSecurityContext(securityFactory.create(run, creator));
			URL feedUrl = interactionFeedSupport.getFeedURI(run).toURL();
			URL webdavUrl = baseurifactory.getRunUriBuilder(run)
					.path("wd/interactions").build().toURL();
			rsr.setInteractionServiceDetails(feedUrl, webdavUrl);
			return run;
		} catch (NoCreateException e) {
			log().warn("failed to build run instance", e);
			throw e;
		} catch (Exception e) {
			log().warn("failed to build run instance", e);
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
			log().warn("failed to build usage record receiver", e);
			return null;
		}
	}

	protected abstract int getDefaultLifetime();

	protected abstract RemoteRunFactory pickLocation(UsernamePrincipal creator,
			Workflow workflow);

	@Autowired(required = true)
	public void setMasterEventFeed(EventDAO masterEventFeed) {
		this.masterEventFeed = masterEventFeed;
	}

	@Override
	public EventDAO getMasterEventFeed() {
		return masterEventFeed;
	}
}
