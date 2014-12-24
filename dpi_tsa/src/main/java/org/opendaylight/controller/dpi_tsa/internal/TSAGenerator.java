package org.opendaylight.controller.dpi_tsa.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.FloodAll;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ubuntu this class generates the flow need to be set in order to match
 *         the TSA policy received see more details in generateRules
 *         generateRules
 * 
 */
public class TSAGenerator {
	static final Logger logger = LoggerFactory.getLogger(SimpleTSAImpl.class);
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

	/**
	 * generate the flow need to be set in order to match the TSA policy
	 * received pseudu-code: for each host in the chain: set tag for every
	 * packet that came from this host forward the packet to the next host for
	 * every switch: forward the packet to the next host according to the tag
	 * 
	 * - no tag forward to the first host - last tag supposed to ignore and let
	 * regular forwarding take in (currently just flood) -every flow is applied
	 * only to the received tsa class
	 * 
	 * @param policyChain
	 *            ordered list of the hosts(IPs) each packet need to traverse
	 * @return dictionary holds for every of node\switch the flows needs to be
	 *         set
	 */
	public Map<Node, List<Flow>> generateRules(String[] policyChain,
			Match TSAClass) {
		List<Switch> switches = _switchManager.getNetworkDevices();
		HashMap<Node, List<Flow>> result = initFlowTable(switches);
		List<HostNodeConnector> policyChainHosts = FlowUtils.findHosts(
				policyChain, _hostTracker);
		short vlanTag = FIRST_VLAN_TAG;

		for (int i = 0; i < policyChainHosts.size(); i++) { // for each host
			HostNodeConnector host = policyChainHosts.get(i);
			HostNodeConnector nextHost = null;
			if (i < policyChainHosts.size() - 1) {
				nextHost = policyChainHosts.get(i + 1);
			}
			Flow tagFlow = generateRouteFromHostFlow(host, nextHost, vlanTag,
					TSAClass);
			result.get(host.getnodeconnectorNode()).add(tagFlow);
			// else regular forwarding
			for (Switch switchNode : switches) {
				// route to the next MB (tunnel)
				Flow routeFlow = generateRouteToHostFlow(host, switchNode,
						vlanTag == FIRST_VLAN_TAG ? 0 : (short) (vlanTag - 1),
						TSAClass);
				result.get(switchNode.getNode()).add(routeFlow);
			}

			vlanTag++;
		}
		// this is a workaround if we can match on NONE_VLAN we need to remove

		for (Switch switchNode : switches) {
			// regular routing (in this case just flood)
			Flow routeFlow = generateFloodFlow(vlanTag - 1, TSAClass);
			result.get(switchNode.getNode()).add(routeFlow);
		}

		return result;

	}

	/**
	 * generate flow that forwards packets tagged with vlanTag* arriving to
	 * switchNode to the next Middlebox (host) if vlan is 0 then packets without
	 * vlan tag
	 * 
	 * @param host
	 * @param switchNode
	 * @param vlanTag
	 * @return
	 */
	private Flow generateRouteToHostFlow(HostNodeConnector host,
			Switch switchNode, short vlanTag, Match tsaClass) {
		short priority = 2;

		Match match = tsaClass.clone();
		// route to first host if no tag exists
		if (vlanTag == 0) { // route to first host
			match.setField(FlowUtils.generateMatchOnTag(0)); // TODO: make this
																// work (match
																// no tag)
			priority = 1; // tmp workaround handle tagged packets first

			// route to host by previous tag
		} else {
			match.setField(FlowUtils.generateMatchOnTag(vlanTag));
		}
		List<Action> actions = new ArrayList<Action>();
		Action output = outputToDst(switchNode.getNode(), host);
		actions.add(output);
		Flow flow = new Flow(match, actions);
		flow.setPriority(priority);
		return flow;

	}

	/**
	 * generate flow that tags a packet coming from the currenthost and passes
	 * it to the nextHost lasthost (nextHost == null) passes control to Standard
	 * routing - currently flood
	 * 
	 * @param currentHost
	 * @param nextHost
	 * @param tag
	 * @return
	 */
	private Flow generateRouteFromHostFlow(HostNodeConnector currentHost,
			HostNodeConnector nextHost, short tag, Match tsaClass) {

		short priority = 3;
		Match match = tsaClass.clone();

		// handle packets arrived from currentHos
		match.setField(new MatchField(MatchType.IN_PORT, currentHost
				.getnodeConnector()));

		List<Action> actions = new ArrayList<Action>();
		// add tag to those packets
		Action action = FlowUtils.generateTagAction(tag);
		actions.add(action);
		// and send them to the next host
		if (nextHost != null) { // last host in chain
			Action output = outputToDst(currentHost.getnodeconnectorNode(),
					nextHost);
			actions.add(output);
		} else {// TODO:last host should pass control to default forwarding
				// using
				// OFPP_PORT
			Action flood = new FloodAll();
			actions.add(flood);
		}
		Flow flow = new Flow(match, actions);
		flow.setPriority(priority);
		return flow;
	}

	/**
	 * returns actions that forward the packets arriving into the sw to the
	 * nextHost this method this action should be set to the switch attached to
	 * the host uses the routing service from constructor
	 * 
	 * @param host
	 * @param nextHost
	 * @return
	 */
	private Action outputToDst(Node sw, HostNodeConnector nextHost) {
		Action result = null;
		Path route = _routing.getRoute(sw, nextHost.getnodeconnectorNode());
		if (route == null) { // destination host is attached to switch
			result = new Output(nextHost.getnodeConnector());
		} else { // forward to destination host
			result = new Output(route.getEdges().get(0).getTailNodeConnector());
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
	 * flood each packets containing the received vlan tag
	 * 
	 * @param tag
	 * @return
	 */
	static Flow generateFloodFlow(int tag, Match tsaClass) {
		Match match = tsaClass.clone();
		match.setField(FlowUtils.generateMatchOnTag(tag));
		Action flood = new FloodAll();
		List<Action> actions = new ArrayList<Action>();
		actions.add(flood);
		Flow flow = new Flow(match, actions);
		flow.setPriority((short) 2);
		return flow;
	}
}
