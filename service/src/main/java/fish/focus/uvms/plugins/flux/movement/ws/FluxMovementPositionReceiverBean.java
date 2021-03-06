/*
﻿Developed with the contribution of the European Commission - Directorate General for Maritime Affairs and Fisheries
© European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or any later version. The IFDM Suite is distributed in
the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have received a
copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 */
package fish.focus.uvms.plugins.flux.movement.ws;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jws.WebService;
import javax.xml.namespace.QName;

import org.jboss.ws.api.annotation.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fish.focus.schema.exchange.movement.v1.SetReportMovementType;
import fish.focus.schema.exchange.plugin.types.v1.PluginType;
import fish.focus.uvms.exchange.model.mapper.ExchangeModuleRequestMapper;
import fish.focus.uvms.plugins.flux.movement.mapper.FluxMessageResponseMapper;
import fish.focus.uvms.plugins.flux.movement.service.PluginService;
import fish.focus.uvms.plugins.flux.movement.service.StartupBean;
import xeu.bridge_connector.v1.RequestType;

@Stateless
@WebService(serviceName = "MovementPositionService", targetNamespace = "urn:xeu:bridge-connector:wsdl:v1", portName = "BridgeConnectorPortType", endpointInterface = "xeu.bridge_connector.wsdl.v1.BridgeConnectorPortType")
@WebContext(contextRoot = "/unionvms/movement-service")
public class FluxMovementPositionReceiverBean extends AbstractFluxReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(FluxMovementPositionReceiverBean.class);

    private static final String FR = "FR";
    private static final String USER = "USER";
    
    @Inject
    private PluginService pluginService;
    
    @Inject
    private StartupBean startupBean;

    @Override
    protected void sendToExchange(RequestType rt){
        try {
            List<SetReportMovementType> movements = FluxMessageResponseMapper.mapToReportMovementTypes(rt, startupBean.getRegisterClassName());
            LOG.info("Going to send [ {} ] movements to exchange.", movements.size());
            Map<QName, String> attributes = rt.getOtherAttributes();
            for (SetReportMovementType movement : movements) {
                String requestStr = ExchangeModuleRequestMapper.createSetMovementReportRequest(movement, attributes.getOrDefault(new QName(USER), PluginType.FLUX.value()), rt.getDF(),
                        Instant.now(), PluginType.FLUX,
                        attributes.get(new QName(FR)), rt.getON());
                pluginService.sendToExchange(requestStr, attributes.get(new QName(FR)));
            }
            LOG.info("Finished sending all movements to exchange.");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not send position to Exchange!", e);
        }
    }

    @Override
    protected StartupBean getStartupBean() {
        return startupBean;
    }
}