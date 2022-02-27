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
package fish.focus.uvms.plugins.flux.movement.service;

import fish.focus.schema.exchange.common.v1.AcknowledgeType;
import fish.focus.schema.exchange.common.v1.AcknowledgeTypeType;
import fish.focus.schema.exchange.common.v1.CommandType;
import fish.focus.schema.exchange.common.v1.CommandTypeType;
import fish.focus.schema.exchange.common.v1.KeyValueType;
import fish.focus.schema.exchange.common.v1.ReportType;
import fish.focus.schema.exchange.common.v1.ReportTypeType;
import fish.focus.schema.exchange.module.v1.ExchangeModuleMethod;
import fish.focus.schema.exchange.movement.v1.MovementType;
import fish.focus.schema.exchange.plugin.types.v1.EmailType;
import fish.focus.schema.exchange.plugin.types.v1.PollType;
import fish.focus.schema.exchange.service.v1.SettingListType;
import fish.focus.uvms.exchange.model.mapper.ExchangePluginResponseMapper;
import fish.focus.uvms.exchange.model.mapper.JAXBMarshaller;
import fish.focus.uvms.plugins.flux.movement.PortInitiator;
import fish.focus.uvms.plugins.flux.movement.exception.PluginException;
import fish.focus.uvms.plugins.flux.movement.message.FluxMessageSenderBean;
import fish.focus.uvms.plugins.flux.movement.producer.PluginToExchangeProducer;
import un.unece.uncefact.data.standard.fluxvesselpositionmessage._4.FLUXVesselPositionMessage;
import java.util.UUID;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@LocalBean
@Stateless
public class PluginService {

    private static final Logger LOG = LoggerFactory.getLogger(FileHandlerBean.class);

    @EJB
    private StartupBean startupBean;

    @EJB
    private FluxMessageSenderBean sender;

    @EJB
    private PortInitiator portInintiator;

    @Inject
    private PluginToExchangeProducer pluginToExchangeProducer;

    @Inject
    @Metric(name = "flux_incoming", absolute = true)
    Counter fluxIncoming;

    @Inject
    @Metric(name = "flux_outgoing", absolute = true)
    Counter fluxOutgoing;

    @Inject
    MetricRegistry registry;

    public void sendToExchange(String requestStr, String from) throws JMSException {
        pluginToExchangeProducer.sendMessageToSpecificQueueWithFunction(requestStr, pluginToExchangeProducer.getDestination(), null, ExchangeModuleMethod.SET_MOVEMENT_REPORT.value(), null);
        registry.counter("flux_incoming_from", new Tag("from", from)).inc();
        fluxIncoming.inc();
    }

    public AcknowledgeType setReport(ReportType report) {
        AcknowledgeTypeType ackType = AcknowledgeTypeType.OK;
        LOG.info(startupBean.getRegisterClassName() + ".report(" + report.getType().name() + ")");
        LOG.debug("timestamp: " + report.getTimestamp());
        String sentMessage = null;
        MovementType movement = report.getMovement();
        if (movement != null && ReportTypeType.MOVEMENT.equals(report.getType())) {
            try {
                FLUXVesselPositionMessage reportSent = sender.sendMovement(movement, report.getRecipient(), report.getRecipientInfo());
                sentMessage = JAXBMarshaller.marshallJaxBObjectToString(reportSent);
                registry.counter("flux_outgoing_to", new Tag("to", report.getRecipient())).inc();
                fluxOutgoing.inc();
            } catch (PluginException ex) {
                registry.counter("flux_outgoing_to_error", new Tag("to", report.getRecipient())).inc();
                LOG.debug("Error when sending report");
                ackType =  AcknowledgeTypeType.NOK;
            }
        }
        return ExchangePluginResponseMapper.mapToAcknowledgeType(report.getLogId(), report.getUnsentMessageGuid(), ackType, sentMessage);
    }

    /**
     * TODO implement
     *
     * @param command
     * @return
     */
    public AcknowledgeTypeType setCommand(CommandType command) {
        LOG.info(startupBean.getRegisterClassName() + ".setCommand(" + command.getCommand().name() + ")");
        LOG.debug("timestamp: " + command.getTimestamp());
        PollType poll = command.getPoll();
        EmailType email = command.getEmail();
        if (poll != null && CommandTypeType.POLL.equals(command.getCommand())) {
            LOG.info("POLL: " + poll.getPollId());
        }
        if (email != null && CommandTypeType.EMAIL.equals(command.getCommand())) {
            LOG.info("EMAIL: subject=" + email.getSubject());
        }
        return AcknowledgeTypeType.OK;
    }

    /**
     * Set the config values for the movement
     *
     * @param settings
     * @return
     */
    public AcknowledgeTypeType setConfig(SettingListType settings) {
        LOG.info(startupBean.getRegisterClassName() + ".setConfig()");
        try {
            for (KeyValueType values : settings.getSetting()) {
                LOG.debug("Setting [ " + values.getKey() + " : " + values.getValue() + " ]");
                startupBean.getSettings().put(values.getKey(), values.getValue());
            }
            portInintiator.updatePort();
            return AcknowledgeTypeType.OK;
        } catch (Exception e) {
            LOG.error("Failed to set config in {}", startupBean.getRegisterClassName());
            return AcknowledgeTypeType.NOK;
        }

    }

    /**
     * Start the movement. Use this to enable functionality in the movement
     *
     * @return
     */
    public AcknowledgeTypeType start() {
        LOG.info(startupBean.getRegisterClassName() + ".start()");
        try {
            startupBean.setIsEnabled(Boolean.TRUE);
            return AcknowledgeTypeType.OK;
        } catch (Exception e) {
            startupBean.setIsEnabled(Boolean.FALSE);
            LOG.error("Failed to start {}", startupBean.getRegisterClassName());
            return AcknowledgeTypeType.NOK;
        }

    }

    /**
     * Start the movement. Use this to disable functionality in the movement
     *
     * @return
     */
    public AcknowledgeTypeType stop() {
        LOG.info(startupBean.getRegisterClassName() + ".stop()");
        try {
            startupBean.setIsEnabled(Boolean.FALSE);
            return AcknowledgeTypeType.OK;
        } catch (Exception e) {
            startupBean.setIsEnabled(Boolean.TRUE);
            LOG.error("Failed to stop {}", startupBean.getRegisterClassName());
            return AcknowledgeTypeType.NOK;
        }
    }

}
