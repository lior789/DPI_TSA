/*
 * Copyright (C) 2014 SDN Hub

 Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.
 You may not use this file except in compliance with this License.
 You may obtain a copy of the License at

    http://www.gnu.org/licenses/gpl-3.0.txt

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied.

 *
 */

package org.opendaylight.dpi_tsa.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.IPProtocols;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.opendaylight.dpi_tsa.ConfigurationHelper;
import org.opendaylight.dpi_tsa.ITrafficSteeringService;
import org.opendaylight.dpi_tsa.listener.TSAListener;
import org.opendaylight.dpi_tsa.listener.TsaSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Common.Protocol.TSA.RawPolicyChain;

public class SimpleTSAImpl implements ITrafficSteeringService {
	private static final Logger logger = LoggerFactory
			.getLogger(SimpleTSAImpl.class);
	private ISwitchManager switchManager = null;
	private IFlowProgrammerService programmer = null;
	private IDataPacketService dataPacketService = null;
	private ITopologyManager topologyManager = null;
	private IRouting routing = null;
	private IfIptoHost hostTracker = null;
	private List<RawPolicyChain> _currentPolicyChain;
	private List<Map<Node, List<Flow>>> _flows = null;
	private Map<String, Match> _trafficClasses;
	private TSAListener _listener;

	/**
	 * Function called by dependency manager after "init ()" is called and after
	 * the services provided by the class are registered in the service registry
	 * 
	 */
	void start() {
		logger.info("Started");
		_flows = new LinkedList<Map<Node, List<Flow>>>();
		_trafficClasses = new HashMap<String, Match>();
		applyPolicyChain(getInitialPolicyChains());
		_listener.start();
	}

	private List<RawPolicyChain> getInitialPolicyChains() {
		List<RawPolicyChain> policyChainsConfig = new ConfigurationHelper()
				.getPolicyChainsConfig();
		return policyChainsConfig;

	}

	/**
	 * apply the received policy chain on the network
	 * 
	 * @param policyChain
	 *            ordered array of hosts in the network each packet should
	 *            traverse (currently only ICMP)
	 */
	@Override
	public void applyPolicyChain(List<RawPolicyChain> policyChains) {
		if (policyChains.equals(_currentPolicyChain)) {
			logger.warn("policy chain already exists: " + policyChains);
			return;
		}
		if (policyChains.size() == 0) {
			logger.warn("got empty policy chain - clean rules ");
			removeFlows(_flows);
			_flows = null;
			_currentPolicyChain = policyChains;
			return;
		}
		logger.info("applying chain: " + policyChains);
		TSAGenerator tsaGenerator = new TSAGenerator(routing, hostTracker,
				switchManager);
		removeFlows(_flows);
		for (RawPolicyChain policyChain : policyChains) {
			Match trafficMatch = FlowUtils.parseMatch(policyChain.trafficClass);
			// Match trafficMatch =
			// _trafficClasses.get(policyChain.trafficClass);
			Map<Node, List<Flow>> chainsFlows = tsaGenerator.generateRules(
					policyChain.chain, trafficMatch);
			programFlows(chainsFlows);
			_flows.add(chainsFlows);

		}

		_currentPolicyChain = policyChains;
	}

	private void removeFlows(List<Map<Node, List<Flow>>> flowsList) {
		logger.info("remove old rules");
		for (Map<Node, List<Flow>> flows : flowsList) {
			for (Node node : flows.keySet()) {
				for (Flow flow : flows.get(node)) {
					programmer.removeFlow(node, flow);
				}
			}
		}
		_flows.clear();

	}

	private void programFlows(Map<Node, List<Flow>> flows) {
		for (Node node : flows.keySet()) {
			for (Flow flow : flows.get(node)) {
				Status status = programmer.addFlow(node, flow);

				if (status.isSuccess()) {
					logger.info(String.format("install flow %s to node %s",
							flow, node));
				} else {
					logger.error(String.format(
							"error while adding flow %s to node %s : %s", flow,
							node, status.getDescription()));
				}
			}
		}
	}

	/**
	 * Function called by the dependency manager before the services exported by
	 * the component are unregistered, this will be followed by a "destroy ()"
	 * calls
	 * 
	 */
	void stop() {
		_listener.stop();
		logger.info("Stopped");
	}

	/**
	 * Function called by the dependency manager when all the required
	 * dependencies are satisfied
	 * 
	 */
	void init() {
		_listener = new TsaSocketListener();
		_listener.setTSA(this);
		_listener.init();
		logger.info("Initialized");
	}

	/**
	 * Function called by the dependency manager when at least one dependency
	 * become unsatisfied or when the component is shutting down because for
	 * example bundle is being stopped.
	 * 
	 */
	void destroy() {
		removeFlows(_flows);
	}

	void setHostTracker(IfIptoHost s) {
		this.hostTracker = s;
	}

	void unsetHostTracker(IfIptoHost s) {
		if (this.hostTracker == s) {
			this.hostTracker = null;
		}
	}

	void setDataPacketService(IDataPacketService s) {
		this.dataPacketService = s;
	}

	void unsetDataPacketService(IDataPacketService s) {
		if (this.dataPacketService == s) {
			this.dataPacketService = null;
		}
	}

	void setRoutingService(IRouting s) {
		this.routing = s;
	}

	void unsetRoutingService(IRouting s) {
		if (this.routing == s) {
			this.routing = null;
		}
	}

	public void setFlowProgrammerService(IFlowProgrammerService s) {
		this.programmer = s;
	}

	public void unsetFlowProgrammerService(IFlowProgrammerService s) {
		if (this.programmer == s) {
			this.programmer = null;
		}
	}

	public void unsetTopologyManager(ITopologyManager s) {
		if (this.topologyManager == s) {
			this.topologyManager = null;
		}
	}

	public void setTopologyManager(ITopologyManager s) {
		this.topologyManager = s;
	}

	void setSwitchManager(ISwitchManager s) {
		logger.debug("SwitchManager set");
		this.switchManager = s;
	}

	void unsetSwitchManager(ISwitchManager s) {
		if (this.switchManager == s) {
			logger.debug("SwitchManager removed!");
			this.switchManager = null;
		}
	}

	@Override
	public List<RawPolicyChain> getPolicyChains() {
		return _currentPolicyChain;
	}

	private List<RawPolicyChain> generateInitialPolicyChains() {
		List<RawPolicyChain> result = new LinkedList<RawPolicyChain>();
		RawPolicyChain tmp = new RawPolicyChain();
		tmp.trafficClass = "OFMatch[eth_type=5,ip_proto=10,nw_dst=10.0.0.7]";
		Match match = new Match();
		match.setField(new MatchField(MatchType.DL_TYPE, EtherTypes.IPv4
				.shortValue()));
		match.setField(new MatchField(MatchType.NW_PROTO, IPProtocols.ICMP
				.byteValue()));
		try {
			match.setField(new MatchField(MatchType.NW_DST, InetAddress
					.getByName("10.0.0.7")));

			_trafficClasses.put(tmp.trafficClass, match);

			tmp.chain = Arrays.asList(InetAddress.getByName("10.0.0.3"),
					InetAddress.getByName("10.0.0.5"),
					InetAddress.getByName("10.0.0.2"));

			result.add(tmp);

			tmp = new RawPolicyChain();
			tmp.trafficClass = "OFMatch[eth_type=5,ip_proto=10,nw_dst=10.0.0.8]";
			match = new Match();
			match.setField(new MatchField(MatchType.DL_TYPE, EtherTypes.IPv4
					.shortValue()));
			match.setField(new MatchField(MatchType.NW_PROTO, IPProtocols.ICMP
					.byteValue()));
			match.setField(new MatchField(MatchType.NW_DST, InetAddress
					.getByName("10.0.0.8")));
			_trafficClasses.put(tmp.trafficClass, match);
			tmp.chain = Arrays.asList(InetAddress.getByName("10.0.0.6"),
					InetAddress.getByName("10.0.0.2"),
					InetAddress.getByName("10.0.0.3"));
			result.add(tmp);
			return result;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}

}
