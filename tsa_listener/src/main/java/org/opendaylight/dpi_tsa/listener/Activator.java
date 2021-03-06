/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.dpi_tsa.listener;

import org.apache.felix.dm.Component;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.dpi_tsa.ITrafficSteeringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator extends ComponentActivatorAbstractBase {

	protected static final Logger logger = LoggerFactory
			.getLogger(Activator.class);

	/**
	 * Function called when the activator starts just after some initializations
	 * are done by the ComponentActivatorAbstractBase.
	 * 
	 */
	@Override
	public void init() {

	}

	/**
	 * Function called when the activator stops just before the cleanup done by
	 * ComponentActivatorAbstractBase
	 * 
	 */
	@Override
	public void destroy() {

	}

	/**
	 * Function that is used to communicate to dependency manager the list of
	 * known implementations for services inside a container
	 * 
	 * 
	 * @return An array containing all the CLASS objects that will be
	 *         instantiated in order to get an fully working implementation
	 *         Object
	 */
	@Override
	public Object[] getImplementations() {
		Object[] res = { TsaSocketListener.class };
		return res;
	}

	/**
	 * Function that is called when configuration of the dependencies is
	 * required.
	 * 
	 * @param c
	 *            dependency manager Component object, used for configuring the
	 *            dependencies exported and imported
	 * @param imp
	 *            Implementation class that is being configured, needed as long
	 *            as the same routine can configure multiple implementations
	 * @param containerName
	 *            The containerName being configured, this allow also optional
	 *            per-container different behavior if needed, usually should not
	 *            be the case though.
	 */
	@Override
	public void configureInstance(Component c, Object imp, String containerName) {

		// if (imp.equals(OpenFlowListener.class)) {
		// Dictionary<String, String> props = new Hashtable<String, String>();
		// props.put("salListenerName", "dpi_tsa");
		// c.setInterface(new String[] { IListenDataPacket.class.getName() },
		// props);
		// }
		if (imp.equals(TsaSocketListener.class)) {
			// register dependent modules
			c.add(createContainerServiceDependency(containerName)
					.setService(ITrafficSteeringService.class)
					.setCallbacks("setTSA", "unsetTSA").setRequired(true));

		}
	}
}
