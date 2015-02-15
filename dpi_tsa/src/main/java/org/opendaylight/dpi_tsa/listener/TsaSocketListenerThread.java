package org.opendaylight.dpi_tsa.listener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.opendaylight.dpi_tsa.ConfigurationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TsaSocketListenerThread extends Thread {
	public Socket _socket;
	public PrintWriter _out;
	public ServerSocket _serverSocket;
	public boolean _running;
	private static final short PORT = 6667;// TODO:move to config file
	private final TsaSocketListener _server;
	static final Logger logger = LoggerFactory
			.getLogger(TsaSocketListener.class);

	public TsaSocketListenerThread(TsaSocketListener tsaSocketListener) {
		_server = tsaSocketListener;
	}

	@Override
	public void run() {
		_running = true;
		try {
			_serverSocket = new ServerSocket();
			ConfigurationHelper config = new ConfigurationHelper();
			_serverSocket.bind(new InetSocketAddress(Inet4Address
					.getByName(config.getProperty("listener.address")), Short
					.valueOf(config.getProperty("listener.port"))));
		} catch (IOException e) {
			logger.error("Failed to start listener: " + e.getMessage());
			e.printStackTrace();
		}
		waitForController();
	}

	private void waitForController() {
		try {
			logger.info("waiting for DPIController..");

			_socket = _serverSocket.accept();
			logger.info("connection established!");
			_out = new PrintWriter(_socket.getOutputStream(), true);
			waitForInstructions();
		} catch (IOException e) {
			logger.error("Failed connecting to dpi controller: "
					+ e.getMessage());
			e.printStackTrace();
		}
	}

	private void waitForInstructions() {
		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(
					_socket.getInputStream()));
			String inputLine;
			logger.info("waiting for requesets..");
			while ((inputLine = in.readLine()) != null) {
				logger.info("got message: " + inputLine);
				_server.handleIncomingMessage(inputLine);
			}
			_socket.close();
			logger.info("TSA listener has stopped listening");
			if (_running) {
				_socket.close();
				waitForController();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

	}

	public void close() {
		try {
			_socket.close();
			_serverSocket.close();
			_running = false;
		} catch (IOException e) {
			logger.error("cant close Connection " + e.getMessage());
		}

	}

	public void sendMessage(String msg) {
		_out.println(msg);
	}
}