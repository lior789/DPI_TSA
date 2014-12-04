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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.protocol_plugin.openflow.internal.FlowProgrammerService;
import org.opendaylight.controller.sal.action.*;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.*;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DpiTsa {
	private static final Logger logger = LoggerFactory.getLogger(DpiTsa.class);
	private ISwitchManager switchManager = null;
	private IFlowProgrammerService programmer = null;
	private IDataPacketService dataPacketService = null;
	private ITopologyManager topologyManager = null;
	private IRouting routing = null;
	private IfIptoHost hostTracker = null;

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

	private void TagHosts(){
		Set<HostNodeConnector> hosts = this.hostTracker.getAllHosts();
		int i = 1001;
		for(HostNodeConnector host:hosts){

            Match match = new Match();
           match.setField(new MatchField(MatchType.IN_PORT, host.getnodeConnector()));
            List<Action> actions = new ArrayList<Action>();
            actions.add(new PushVlan(EtherTypes.VLANTAGGED,0,0,i));
            Flow flow = new Flow(match,actions);
            flow.setHardTimeout((short) 360);
            Status status = programmer.addFlow(host.getnodeconnectorNode(), flow);
            if (!status.isSuccess()) {
                logger.warn(
                        "SDN Plugin failed to program the flow: {}. The failure is: {}",
                        flow, status.getDescription());
            }
            else{
            logger.info(String.format("add flow vlan %s to node %s",i,host.getnodeconnectorNode()));
            }
            i++;
		}
	}
	
	private void printShortestPaths() {
		logger.info("graph data:");
		Set<HostNodeConnector> hosts = this.hostTracker.getAllHosts();
		logger.info(hosts.size() + " hosts found");
		for (HostNodeConnector host1 : hosts) {
			for (HostNodeConnector host2 : hosts) {
				logger.info(String.format("%s -> %s:",
						host1.getNetworkAddressAsString(),
						host2.getNetworkAddressAsString()));
				logger.info(routing.toString());
				Path route = routing.getRoute(host1.getnodeconnectorNode(),
						host2.getnodeconnectorNode());
				if(route == null){
					logger.info("hosts connected to the same switch");
				}
				else{
				logger.info(route.toString());
				}
			}
		}
	}

	/**
	 * Function called by the dependency manager when at least one dependency
	 * become unsatisfied or when the component is shutting down because for
	 * example bundle is being stopped.
	 * 
	 */
	void destroy() {
	}

	/**
	 * Function called by dependency manager after "init ()" is called and after
	 * the services provided by the class are registered in the service registry
	 * 
	 */
	void start() {
		logger.info("Started");
		try {
			printShortestPaths();
			TagHosts();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
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

}
