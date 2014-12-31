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

package org.opendaylight.controller.dpi_tsa.internal;

import java.util.Arrays;
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
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.IPProtocols;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTSAImpl implements ITrafficSteeringService,
		IListenDataPacket {
	private static final Logger logger = LoggerFactory
			.getLogger(SimpleTSAImpl.class);
	private ISwitchManager switchManager = null;
	private IFlowProgrammerService programmer = null;
	private IDataPacketService dataPacketService = null;
	private ITopologyManager topologyManager = null;
	private IRouting routing = null;
	private IfIptoHost hostTracker = null;
	private TsaListener _tsaListener;
	private String[] _lastPolicyChain;
	private Map<Node, List<Flow>> _flows = null;

	/**
	 * Function called by dependency manager after "init ()" is called and after
	 * the services provided by the class are registered in the service registry
	 * 
	 */
	void start() {
		logger.info("Started");
		_tsaListener = new TsaListener();
		_tsaListener.setTsService(this);
		_tsaListener.setDataPacketService(this.dataPacketService);
		_tsaListener.setSwitchManager(this.switchManager);
		_tsaListener.setFlowProgrammer(this.programmer);
		_tsaListener.start();
		/**
		 * String[] policyChain = new String[] { "10.0.0.3", "10.0.0.1",
		 * "10.0.0.2" }; applyPolicyChain(policyChain);
		 **/
	}

	/**
	 * apply the received policy chain on the network
	 * 
	 * @param policyChain
	 *            ordered array of hosts in the network each packet should
	 *            traverse (currently only ICMP)
	 */
	@Override
	public void applyPolicyChain(String[] policyChain) {
		if (Arrays.equals(policyChain, _lastPolicyChain)) {
			logger.warn("policy chain already exists: "
					+ Arrays.toString(policyChain));
			return;
		}
		if (policyChain.length == 0) {
			logger.warn("got empty policy chain - clean rules ");
			removeFlows(_flows);
			_flows = null;
			_lastPolicyChain = policyChain;
			return;
		}
		logger.info("applying chain: " + Arrays.toString(policyChain));
		try {
			TSAGenerator tsaGenerator = new TSAGenerator(routing, hostTracker,
					switchManager);
			removeFlows(_flows);
			_flows = tsaGenerator.generateRules(policyChain,
					SimpleTSAImpl.generateTSAClassMatch());
			programFlows(_flows);
			_lastPolicyChain = policyChain;

		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private void removeFlows(Map<Node, List<Flow>> flows) {
		if (flows == null) {
			return;
		}
		logger.info("remove old rules");
		for (Node node : flows.keySet()) {
			for (Flow flow : flows.get(node)) {
				programmer.removeFlow(node, flow);
			}

		}
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
		logger.info("Stopped");
	}

	/**
	 * return Match object representing the traffic that should traverse the TSA
	 * currently ICMP hard-coded
	 * 
	 * @return
	 */
	private static Match generateTSAClassMatch() {
		Match match = new Match();
		match.setField(new MatchField(MatchType.DL_TYPE, EtherTypes.IPv4
				.shortValue()));
		match.setField(new MatchField(MatchType.NW_PROTO, IPProtocols.ICMP
				.byteValue()));
		return match;
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

	/**
	 * Function called by the dependency manager when all the required
	 * dependencies are satisfied
	 * 
	 */
	void init() {
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

	@Override
	public PacketResult receiveDataPacket(RawPacket inPkt) {
		return this._tsaListener.receiveDataPacket(inPkt);
	}

}
