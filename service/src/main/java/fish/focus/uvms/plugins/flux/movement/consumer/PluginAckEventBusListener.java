/*
 * ﻿Developed with the contribution of the European Commission - Directorate General for Maritime Affairs and Fisheries
 * © European Union, 2015-2016.
 *
 * This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later version. The IFDM Suite is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have received a
 * copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 */

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
package fish.focus.uvms.plugins.flux.movement.consumer;

import fish.focus.schema.exchange.registry.v1.ExchangeRegistryBaseRequest;
import fish.focus.schema.exchange.registry.v1.RegisterServiceResponse;
import fish.focus.schema.exchange.registry.v1.UnregisterServiceResponse;
import fish.focus.uvms.commons.message.api.MessageConstants;
import fish.focus.uvms.exchange.model.mapper.JAXBMarshaller;
import fish.focus.uvms.plugins.flux.movement.constants.MovementPluginConstants;
import fish.focus.uvms.plugins.flux.movement.service.StartupBean;
import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = MessageConstants.SUBSCRIPTION_DURABILITY_STR, propertyValue = MessageConstants.DURABLE_CONNECTION),
        @ActivationConfigProperty(propertyName = MessageConstants.DESTINATION_TYPE_STR, propertyValue = MessageConstants.DESTINATION_TYPE_TOPIC),
        @ActivationConfigProperty(propertyName = MessageConstants.DESTINATION_LOOKUP_STR, propertyValue = MessageConstants.EVENT_BUS_TOPIC),
        @ActivationConfigProperty(propertyName = MessageConstants.SUBSCRIPTION_NAME_STR, propertyValue = MovementPluginConstants.SUBSCRIPTION_NAME_AC),
        @ActivationConfigProperty(propertyName = MessageConstants.CLIENT_ID_STR, propertyValue = MovementPluginConstants.CLIENT_ID_AC),
        @ActivationConfigProperty(propertyName = MessageConstants.MESSAGE_SELECTOR_STR, propertyValue = MovementPluginConstants.MESSAGE_SELECTOR_AC)
})
public class PluginAckEventBusListener implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(PluginAckEventBusListener.class);

    @EJB
    private StartupBean startupService;

    @Override
    public void onMessage(Message inMessage) {
        LOG.info("Eventbus listener for movement at selector: {} got a message", startupService.getPluginResponseSubscriptionName());
        TextMessage textMessage = (TextMessage) inMessage;
        try {
            ExchangeRegistryBaseRequest request = tryConsumeRegistryBaseRequest(textMessage);
            if (request == null) {
                handlePluginFault(textMessage);
            } else {
                switch (request.getMethod()) {
                    case REGISTER_SERVICE:
                        RegisterServiceResponse registerResponse = JAXBMarshaller.unmarshallTextMessage(textMessage, RegisterServiceResponse.class);
                        startupService.setWaitingForResponse(Boolean.FALSE);
                        switch (registerResponse.getAck().getType()) {
                            case OK:
                                LOG.info("Register OK");
                                startupService.setIsRegistered(Boolean.TRUE);
                                break;
                            case NOK:
                                LOG.info("Register NOK: " + registerResponse.getAck().getMessage());
                                startupService.setIsRegistered(Boolean.FALSE);
                                break;
                            default:
                                LOG.error("[ Type not supperted: ]" + request.getMethod());
                        }
                        break;
                    case UNREGISTER_SERVICE:
                        UnregisterServiceResponse unregisterResponse = JAXBMarshaller.unmarshallTextMessage(textMessage, UnregisterServiceResponse.class);
                        switch (unregisterResponse.getAck().getType()) {
                            case OK:
                                LOG.info("Unregister OK");
                                break;
                            case NOK:
                                LOG.info("Unregister NOK");
                                break;
                            default:
                                LOG.error("[ Ack type not supported ] ");
                                break;
                        }
                        break;
                    default:
                        LOG.error("Not supported method");
                        break;
                }
            }
        } catch (RuntimeException e) {
            LOG.error("[ Error when receiving message in movement ]", e);
        }
    }

    private void handlePluginFault(TextMessage fault) {
        try {
            LOG.error(startupService.getPluginResponseSubscriptionName() + " received fault: " + fault.getText() + " : " );
        } catch (JMSException e) {
            LOG.error("Could not get incoming text message in FLUX");
        }
    }

    private ExchangeRegistryBaseRequest tryConsumeRegistryBaseRequest(TextMessage textMessage) {
        try {
            return JAXBMarshaller.unmarshallTextMessage(textMessage, ExchangeRegistryBaseRequest.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}