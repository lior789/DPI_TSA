package org.opendaylight.controller.dpi_tsa.internal;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.action.*;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.NodeConnector.NodeConnectorIDType;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.IPProtocols;
import org.opendaylight.controller.sal.utils.NodeConnectorCreator;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.openflow.protocol.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ubuntu
 * 
 */
public class TSAGenerator {
	private static final Logger logger = LoggerFactory.getLogger(DpiTsa.class);
	private static final short FIRST_VLAN_TAG = 300;
	private final IRouting _routing;
	private final IfIptoHost _hostTracker;
	private final ISwitchManager _switchManager;

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
			HostNodeConnector nextHost = null;
			if (i < policyChainHosts.size() - 1) {
				nextHost = policyChainHosts.get(i + 1);
			}
			Flow tagFlow = routeFromHostFlow(host, nextHost, vlanTag);
			result.get(host.getnodeconnectorNode()).add(tagFlow);
			// else regular forwarding
			for (Switch switchNode : switches) {
				// route to the next MB (tunnel)
				Flow routeFlow = routeToHostFlow(host, switchNode, vlanTag);
				result.get(switchNode.getNode()).add(routeFlow);
			}

			vlanTag++;
		}
		// this is a workaround if we can match on NONE_VLAN we need to remove

		for (Switch switchNode : switches) {
			// regular routing (in this case just flood)
			Flow routeFlow = AddFloodFlow(vlanTag - 1);
			result.get(switchNode.getNode()).add(routeFlow);
		}

		return result;

	}

	private Flow AddFloodFlow(int vlanTag) {
		Match match = new Match();
		match.setField(new MatchField(MatchType.DL_VLAN, (short) (vlanTag - 1)));
		Action flood = new FloodAll();
		List<Action> actions = new ArrayList<Action>();
		actions.add(flood);
		Flow flow = new Flow(match, actions);
		flow.setPriority((short) 2);
		return flow;
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
		short priority = 2;

		Match match = matchICMP();
		// route to first MB if no vlan exists
		if (vlanTag == FIRST_VLAN_TAG) { // route to first host
			match.setField(new MatchField(MatchType.DL_VLAN,
					MatchType.DL_VLAN_NONE)); // TODO: make this work!!!
			priority = 1; // tmp workaround handle tagged packets first

			// route to host by previous tag
		} else {
			match.setField(new MatchField(MatchType.DL_VLAN,
					(short) (vlanTag - 1)));
		}

		List<Action> actions = new ArrayList<Action>();
		Action output = outputToDest(switchNode, host);
		actions.add(output);
		Flow flow = new Flow(match, actions);
		flow.setPriority(priority);
		return flow;

	}

	private Flow routeFromHostFlow(HostNodeConnector currentMB,
			HostNodeConnector nextMB, short vlanTag) {

		short priority = 3;
		Match match = matchICMP();
		match.setField(new MatchField(MatchType.IN_PORT, currentMB
				.getnodeConnector()));

		List<Action> actions = new ArrayList<Action>();
		Action action = new PushVlan(EtherTypes.VLANTAGGED.intValue(), 0, 0,
				vlanTag);
		actions.add(action);
		if (nextMB != null) { // last MB in chain
			Action output = outputToDst(currentMB, nextMB);
			actions.add(output);
		} else {
			NodeConnector table_loop = NodeConnectorCreator
					.createOFNodeConnector(
							(short) OFPort.OFPP_TABLE.getValue(),
							currentMB.getnodeconnectorNode());
			Action loopback = new Output(table_loop);
			actions.add(loopback);
		}
		Flow flow = new Flow(match, actions);
		flow.setPriority(priority);
		return flow;
	}

	private Action outputToDest(Switch switchNode, HostNodeConnector host) {
		Action output;
		Path route = _routing.getRoute(switchNode.getNode(),
				host.getnodeconnectorNode());
		if (route == null) { // destination host is attached to switch
			output = new Output(host.getnodeConnector());
		} else { // forward to destination host
			output = new Output(route.getEdges().get(0).getTailNodeConnector());
		}
		return output;
	}

	private Match matchICMP() {
		Match match = new Match();
		match.setField(new MatchField(MatchType.DL_TYPE, EtherTypes.IPv4
				.shortValue()));
		match.setField(new MatchField(MatchType.NW_PROTO, IPProtocols.ICMP
				.byteValue()));
		return match;
	}

	private Action outputToDst(HostNodeConnector host,
			HostNodeConnector nextHost) {
		Action result = null;
		Path route = _routing.getRoute(host.getnodeconnectorNode(),
				nextHost.getnodeconnectorNode());
		if (route == null) { // destination host is attached to switch
			result = new Output(nextHost.getnodeConnector());
		} else { // forward to destination host
			result = new Output(route.getEdges().get(0).getTailNodeConnector());
		}
		return result;
	}

	private List<HostNodeConnector> findHosts(String[] policyChain) {
		List<HostNodeConnector> policyChainHosts = new ArrayList<HostNodeConnector>();
		for (String mbAddress : policyChain) {
			try {
				HostNodeConnector host;
				logger.info(String.format("looking for host %s ..", mbAddress));
				InetAddress hostAddress = InetAddress.getByName(mbAddress);
				host = _hostTracker.discoverHost(hostAddress).get();
				if (host != null) {
					logger.info(String.format("host %s found!", mbAddress));
					policyChainHosts.add(host);
				} else {
					logger.error(String.format("host %s not found :(",
							mbAddress));
				}

			} catch (Exception e) {
				logger.error(String.format(
						"Problem occoured while looking for host %s : %s",
						mbAddress, e.getMessage()));

			}
		}
		return policyChainHosts;
	}
}
