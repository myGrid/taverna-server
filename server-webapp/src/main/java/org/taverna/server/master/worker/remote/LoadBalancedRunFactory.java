package org.taverna.server.master.worker.remote;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.taverna.server.localworker.remote.RemoteRunFactory;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.utils.UsernamePrincipal;
import org.taverna.server.master.worker.AbstractRunFactory;

public class LoadBalancedRunFactory extends AbstractRunFactory {
	private Log log = LogFactory
			.getLog("Taverna.Server.Worker.RemoteLoadBalanced");

	@Override
	protected int getDefaultLifetime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected RemoteRunFactory pickLocation(UsernamePrincipal creator,
			Workflow workflow) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAllowingRunsToStart() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected Log log() {
		return log;
	}
}
