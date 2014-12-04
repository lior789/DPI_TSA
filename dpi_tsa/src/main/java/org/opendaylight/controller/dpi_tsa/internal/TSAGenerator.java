package org.opendaylight.controller.dpi_tsa.internal;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TSAGenerator {
	private static final Logger logger = LoggerFactory.getLogger(DpiTsa.class);
	private IRouting _routing;
	private IfIptoHost _hostTracker;
	private ISwitchManager _switchManager;

	public TSAGenerator(IRouting routing, IfIptoHost hostTracker,
			ISwitchManager switchManager) {
		this._routing = routing;
		this._hostTracker = hostTracker;
		this._switchManager = switchManager;
	}

	public void generateRules(String[] policyChain) {

		List<HostNodeConnector> policyChainHosts = findHosts(policyChain);
		policyChainHosts.size();

		List<Switch> switches = _switchManager.getNetworkDevices();
		for (Switch switchNode : switches) {

		}

	}

	private List<HostNodeConnector> findHosts(String[] policyChain) {
		List<HostNodeConnector> policyChainHosts = new ArrayList<HostNodeConnector>();
		for (String mbAddress : policyChain) {
			try {
				HostNodeConnector host;
				logger.info("looking for host %s ..", mbAddress);
				host = _hostTracker.discoverHost(
						InetAddress.getByName(mbAddress)).get();
				logger.info("host %s found!", mbAddress);
				policyChainHosts.add((HostNodeConnector) host);
			} catch (Exception e) {
				logger.error("Problem occoured while looking for host %s : %s",
						mbAddress, e.getMessage());

			}
		}
		return policyChainHosts;
	}
}
