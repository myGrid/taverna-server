package org.taverna.server.master.worker.remote;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Comparator;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

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

	private Comparator<RemoteRunFactory> loadComparator;

	@Override
	protected RemoteRunFactory pickLocation(UsernamePrincipal creator,
			Workflow workflow) throws RemoteException {
		Registry r = getRegistry();
		PriorityQueue<RemoteRunFactory> queue = new PriorityQueue<RemoteRunFactory>(
				1, loadComparator);
		for (String name : r.list()) {
			if (!name.startsWith("server-worker."))
				continue;
			try {
				queue.add((RemoteRunFactory) r.lookup(name));
			} catch (NotBoundException e) {
			} catch (ClassCastException e) {
			}
		}
		while (!queue.isEmpty()) {
			RemoteRunFactory rrf = queue.remove();
			String nonce = null; // TODO
			if (response(nonce).equals(rrf.challenge(nonce)) && rrf.countOperatingRuns() < operatingLimit())
				return rrf;
		}
		return createNewFactory();
	}

	private RemoteRunFactory createNewFactory() {
		return null; // TODO
	}

	private int operatingLimit() {
		// TODO
		return 0;
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

	private Registry registry;

	protected Registry getRegistry() throws RemoteException {
		if (registry == null) {
			synchronized (this) {
				if (registry == null) {
					registry = findOrCreateRegistry();
				}
			}
		}
		return registry;
	}

	private Registry findOrCreateRegistry() throws RemoteException {
		try {
			Registry r = LocateRegistry.getRegistry();
			r.list();
			return r;
		} catch (RemoteException e) {
			// No working registry available
			return LocateRegistry.createRegistry(1099);
		}
	}
}
