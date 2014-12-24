package org.opendaylight.controller.dpi_tsa.internal;

import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Controller;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.UDP;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.IPProtocols;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.switchmanager.Switch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class TsaListener implements IListenDataPacket {

	private ITrafficSteeringService _tsa;
	private IDataPacketService dataPacketService;
	private static final Logger logger = LoggerFactory
			.getLogger(TsaListener.class);
	private static final short IDENTIFICATION_PORT = 6666;
	// TODO:move to config file
	private ISwitchManager switchManager;
	private IFlowProgrammerService programmer;

	@Override
	public PacketResult receiveDataPacket(RawPacket inPkt) {
		Packet packet = this.dataPacketService.decodeDataPacket(inPkt);
		if (isTsaMessage(packet)) {
			logger.info("got message from DPI controller");
			String[] chain = parseTsaChain(packet);
			this._tsa.applyPolicyChain(chain);
			return PacketResult.CONSUME;
		} else {
			return PacketResult.IGNORED;
		}
	}

	private String[] parseTsaChain(Packet packet) {
		UDP udp = (UDP) packet.getPayload().getPayload();
		byte[] payload = udp.getRawPayload();
		String data = new String(payload);
		Gson gson = new Gson();
		String[] chain = gson.fromJson(data, String[].class);
		return chain;
	}

	private boolean isTsaMessage(Packet packet) {
		if (!(packet instanceof Ethernet))
			return false;
		packet = packet.getPayload();
		if (!(packet instanceof IPv4))
			return false;
		packet = packet.getPayload();
		if (!(packet instanceof UDP))
			return false;
		UDP udp = (UDP) packet;
		return udp.getDestinationPort() == IDENTIFICATION_PORT;

	}

	public void setTsService(ITrafficSteeringService tsa) {
		this._tsa = tsa;
	}

	public void start() {
		logger.info("Tsa listener has started");
		setPassMessagesFlow();
	}

	private void setPassMessagesFlow() {
		Match match = getControllerPacketsMatch();
		Action action = new Controller();
		LinkedList<Action> actions = new LinkedList<Action>();
		actions.add(action);
		Flow flow = new Flow(match, actions);
		flow.setPriority((short) 200);
		List<Switch> switches = switchManager.getNetworkDevices();
		for (Switch sw : switches) {
			Status status = this.programmer.addFlow(sw.getNode(), flow);
			if (!status.isSuccess()) {
				logger.error("problem while adding flow: "
						+ status.getDescription());
			}
		}
		logger.info("set flows on all nodes");
	}

	private Match getControllerPacketsMatch() {
		Match result = new Match();
		result.setField(new MatchField(MatchType.DL_TYPE, EtherTypes.IPv4
				.shortValue()));
		result.setField(new MatchField(MatchType.TP_DST, IDENTIFICATION_PORT));
		result.setField(new MatchField(MatchType.NW_PROTO, IPProtocols.UDP
				.byteValue()));
		return result;
	}

	public void setDataPacketService(IDataPacketService dataPacketService) {
		this.dataPacketService = dataPacketService;
		// TODO Auto-generated method stub

	}

	public void setSwitchManager(ISwitchManager switchManager) {
		this.switchManager = switchManager;
		// TODO Auto-generated method stub

	}

	public void setFlowProgrammer(IFlowProgrammerService programmer) {
		this.programmer = programmer;

	}

}
