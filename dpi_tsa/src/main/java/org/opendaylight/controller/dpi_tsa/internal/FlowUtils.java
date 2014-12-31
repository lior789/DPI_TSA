package org.opendaylight.controller.dpi_tsa.internal;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.PushVlan;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.utils.EtherTypes;

public class FlowUtils {

	/**
	 * return match field representing packet with tag tag=0 means no tag
	 * 
	 * @param tag
	 * @return
	 */

	static List<MatchField> generateMatchOnTag(int tag) {
		LinkedList<MatchField> result = new LinkedList<MatchField>();
		if (tag == 0) {
			// TODO: make this work!!!
			result.add(new MatchField(MatchType.DL_VLAN, MatchType.DL_VLAN_NONE));
		} else {
			result.add(new MatchField(MatchType.DL_VLAN, (short) (tag)));
		}
		return result;
	}

	/**
	 * this method return list of HostNodeConnector corresponding to the input
	 * IP list the method uses the host tacker service received on the
	 * constructor
	 * 
	 * @param hosts
	 * @param hostTarcker
	 *            TODO
	 * @return
	 */
	static List<HostNodeConnector> findHosts(String[] hosts,
			IfIptoHost hostTarcker) {
		List<HostNodeConnector> policyChainHosts = new ArrayList<HostNodeConnector>();
		for (String mbAddress : hosts) {
			try {
				HostNodeConnector host;
				TSAGenerator.logger.info(String.format(
						"looking for host %s ..", mbAddress));
				InetAddress hostAddress = InetAddress.getByName(mbAddress);
				host = hostTarcker.discoverHost(hostAddress).get();
				if (host != null) {
					TSAGenerator.logger.info(String.format("host %s found!",
							mbAddress));
					policyChainHosts.add(host);
				} else {
					TSAGenerator.logger.error(String.format(
							"host %s not found :(", mbAddress));
				}

			} catch (Exception e) {
				TSAGenerator.logger.error(String.format(
						"Problem occoured while looking for host %s : %s",
						mbAddress, e.getMessage()));

			}
		}
		return policyChainHosts;
	}

	/**
	 * return action that tags the packets
	 * 
	 * @param tag
	 * @return
	 */
	static Action generateTagAction(short tag) {
		return new PushVlan(EtherTypes.VLANTAGGED.intValue(), 0, 0, tag);
	}

	/**
	 * this method used to set multiple fields into a match object
	 * 
	 * @param match
	 *            the match to update
	 * @param matchFields
	 *            list of matchfields to set
	 */
	public static void setFields(Match match, List<MatchField> matchFields) {
		for (MatchField matchField : matchFields) {
			match.setField(matchField);
		}
	}

}
