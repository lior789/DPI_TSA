package org.opendaylight.controller.dpi_tsa.internal;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.action.PushVlan;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.http.MatcherType;

/**
 * @author ubuntu
 *
 */
public class TSAGenerator {
	private static final Logger logger = LoggerFactory.getLogger(DpiTsa.class);
	private static final short FIRST_VLAN_TAG = 300;
	private IRouting _routing;
	private IfIptoHost _hostTracker;
	private ISwitchManager _switchManager;

	public TSAGenerator(IRouting routing, IfIptoHost hostTracker,
			ISwitchManager switchManager) {
		this._routing = routing;
		this._hostTracker = hostTracker;
		this._switchManager = switchManager;
	}

	public Map<Node, List<Flow>> generateRules(String[] policyChain) {
		List<Switch> switches = _switchManager.getNetworkDevices();
		HashMap<Node, List<Flow>> result = initFlowTable(switches);
		List<HostNodeConnector> policyChainHosts = findHosts(policyChain);
		short vlanTag = FIRST_VLAN_TAG;

		for (int i = 0; i < policyChainHosts.size(); i++) { // for each MB
			HostNodeConnector host = policyChainHosts.get(i);			
			if(i < policyChainHosts.size()-1){ //route from MB to the next MB (unless we reach the final mb)
				HostNodeConnector nextHost = policyChainHosts.get(i+1);
				Flow tagFlow = routeFromHostFlow(host,nextHost, vlanTag); 
				result.get(host.getnodeconnectorNode()).add(tagFlow);
			}
			//else regular forwarding
			for (Switch switchNode : switches) {
				Flow routeFlow = routeToHostFlow(host, switchNode, vlanTag); //route to the next MB (tunnel)
				result.get(switchNode.getNode()).add(routeFlow);
			}
			vlanTag++;
		}
		return result;

	}

	private HashMap<Node, List<Flow>> initFlowTable(List<Switch> switches) {
		HashMap<Node, List<Flow>> result = new HashMap<Node, List<Flow>>();
		// init result
		for (Switch switchNode : switches) {
			result.put(switchNode.getNode(), new ArrayList<Flow>());
		}
		return result;
	}
	/**
	 * 
	 * @param host
	 * @param switchNode
	 * @param vlanTag
	 * @return
	 */
	private Flow routeToHostFlow(HostNodeConnector host, Switch switchNode,
			short vlanTag) {
		
		Path route = _routing.getRoute(switchNode.getNode(), host.getnodeconnectorNode());
		Match match = new Match();
		if(vlanTag == FIRST_VLAN_TAG){ //route to first host
			match.setField(new MatchField(MatchType.DL_VLAN,(short)(MatchType.DL_VLAN_NONE)));
		}
		else{ //route to host by previous tag
			match.setField(new MatchField(MatchType.DL_OUTER_VLAN,(short)(vlanTag-1)));
		}
		List<Action> actions = new ArrayList<Action>();
		
		if(route == null){ //destination host is attached to switch
			actions.add(new Output(host.getnodeConnector()));
		}
		else{ // forward to destination host
			actions.add(new Output(route.getEdges().get(0).getHeadNodeConnector()));
		}
		Flow flow = new Flow(match,actions);
		flow.setPriority((short)1);
		return flow;

	}

	private Flow routeFromHostFlow(HostNodeConnector host, HostNodeConnector nextHost, short vlanTag) {		
		Path route = _routing.getRoute(host.getnodeconnectorNode(), nextHost.getnodeconnectorNode());
		Match match = new Match();
		match.setField(new MatchField(MatchType.IN_PORT, host.getnodeConnector()));
		List<Action> actions = new ArrayList<Action>();
		PushVlan pushVlan = new PushVlan(EtherTypes.VLANTAGGED, 0, 0, vlanTag);
		if(!pushVlan.isValid())
			logger.info("not a valid action");
		actions.add(pushVlan);
		if(route == null){ //destination host is attached to switch
			actions.add(new Output(nextHost.getnodeConnector()));
		}
		else{ // forward to destination host
			actions.add(new Output(route.getEdges().get(0).getHeadNodeConnector()));
		}
		Flow flow = new Flow(match, actions);
		flow.setPriority((short)2);
		return flow;
	}

	private List<HostNodeConnector> findHosts(String[] policyChain) {
		List<HostNodeConnector> policyChainHosts = new ArrayList<HostNodeConnector>();
		for (String mbAddress : policyChain) {
			try {
				HostNodeConnector host;
				logger.info(String.format("looking for host %s ..", mbAddress));
				InetAddress hostAddress = InetAddress.getByName(mbAddress);
				host = _hostTracker.discoverHost(hostAddress).get();
				if(host != null){
					logger.info(String.format("host %s found!", mbAddress));
					policyChainHosts.add((HostNodeConnector) host);
				}
				else{
					logger.error(String.format("host %s not found :(", mbAddress));
				}
				
			} catch (Exception e) {
				logger.error(String.format("Problem occoured while looking for host %s : %s",
						mbAddress, e.getMessage()));

			}
		}
		return policyChainHosts;
	}
}
