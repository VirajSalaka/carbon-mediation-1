/**
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * RabbitMQInjectHandler uses to mediate the received RabbitMQ message
 */
public class RabbitMQInjectHandler {
    private static final Log log = LogFactory.getLog(RabbitMQInjectHandler.class);

    private String injectingSeq;
    private String onErrorSeq;
    private boolean sequential;
    private SynapseEnvironment synapseEnvironment;
    private SequenceMediator seq;

    public RabbitMQInjectHandler(String injectingSeq, String onErrorSeq, boolean sequential,
                                 SynapseEnvironment synapseEnvironment) throws SynapseException {
        this.injectingSeq = injectingSeq;
        if (injectingSeq == null || injectingSeq.equals("")) {
            String msg = "Injecting Sequence name is not specified.";
            log.error(msg);
            throw new SynapseException(msg);
        }
        seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration().getSequence(injectingSeq);
        if (seq == null) {
            throw new SynapseException("Specified injecting sequence: " + injectingSeq + "is invalid.");
        }
        if (!seq.isInitialized()) {
            seq.init(synapseEnvironment);
        }
        this.onErrorSeq = onErrorSeq;
        this.sequential = sequential;
        this.synapseEnvironment = synapseEnvironment;
    }

    /**
     * Determine the message builder to use, set the message payload to the message context and
     * inject the message.
     *
     * @param message                    RabbitMQ message consumed
     * @param inboundName                Inbound Name
     * @return Whether message should be Acked/Rejected/Requeued and whether mediationError is true/false
     */
    public States invokeAndReturnAckState(RabbitMQMessage message, String inboundName) {

        org.apache.synapse.MessageContext msgCtx = null;
        try {
            msgCtx = createMessageContext();
            log.debug("Processed RabbitMQ Message ");
            MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                    .getAxis2MessageContext();
            // Determine the message builder to use
            String amqpCorrelationID = message.getCorrelationId();
            if (amqpCorrelationID != null && amqpCorrelationID.length() > 0) {
                msgCtx.setProperty(RabbitMQConstants.CORRELATION_ID, amqpCorrelationID);
            } else {
                msgCtx.setProperty(RabbitMQConstants.CORRELATION_ID, message.getMessageId());
            }

            axis2MsgCtx.setProperty(MessageContext.TRANSPORT_HEADERS, RabbitMQUtils.getTransportHeaders(message));

            String contentType = message.getContentType();
            Builder builder = null;
            if (contentType == null) {
                log.warn("Unable to determine content type for message " +
                        msgCtx.getMessageID() + " setting to text/plain");
                contentType = RabbitMQConstants.DEFAULT_CONTENT_TYPE;
                message.setContentType(contentType);
            }

            int index = contentType.indexOf(';');
            String type = index > 0 ? contentType.substring(0, index)
                    : contentType;
            try {
                builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
            } catch (AxisFault axisFault) {
                log.error("Error while creating message builder :: "
                        + axisFault.getMessage());

            }
            if (builder == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No message builder found for type '" + type
                            + "'. Falling back to SOAP.");
                }
                builder = new SOAPBuilder();
            }

            OMElement documentElement = null;
            // set the message payload to the message context
            InputStream in = new ByteArrayInputStream(message.getBody());
            try {
                documentElement = builder.processDocument(in, contentType,
                        axis2MsgCtx);
            } catch (AxisFault axisFault) {
                log.error("Error while processing message :: "
                        + axisFault.getMessage());
            }

            try {
                msgCtx.setEnvelope(TransportUtils
                        .createSOAPEnvelope(documentElement));
            } catch (AxisFault axisFault) {
                log.error("Error while setting message payload to the message context :: "
                        + axisFault.getMessage());
            }

            if (seq != null) {
                if (log.isDebugEnabled()) {
                    log.debug("injecting message to sequence : " + injectingSeq);
                }
                seq.setErrorHandler(onErrorSeq);
                msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
                msgCtx.setProperty(SynapseConstants.INBOUND_ENDPOINT_NAME, inboundName);
                msgCtx.setProperty(SynapseConstants.ARTIFACT_NAME, SynapseConstants.FAIL_SAFE_MODE_INBOUND_ENDPOINT + inboundName);
                synapseEnvironment.injectInbound(msgCtx, seq, sequential);
            } else {
                log.error("Sequence: " + injectingSeq + " not found");
            }

            if (readMessageCtxProperty(msgCtx, RabbitMQConstants.SET_ROLLBACK_ONLY, false)) {
                if (readMessageCtxProperty(msgCtx, RabbitMQConstants.SET_REQUEUE_ON_ROLLBACK, true)) {
                    return new States(RabbitMQAckStates.REJECT_AND_REQUEUE, false);
                } else {
                    return new States(RabbitMQAckStates.REJECT, false);
                }
            }

            return new States(RabbitMQAckStates.ACK, false);

        } catch (Exception e) {         //we need to handle any exception upon injecting to mediation
            if (readMessageCtxProperty(msgCtx, RabbitMQConstants.SET_REQUEUE_ON_ROLLBACK, false)) {
                log.error("Error while mediating message. Message is requeued as " +
                        "SET_REQUEUE_ON_ROLLBACK property is set to true.", e);
                return new States(RabbitMQAckStates.REJECT_AND_REQUEUE, true);
            }
            log.error("Error while mediating message. Message is rejected.", e);
            return new States(RabbitMQAckStates.REJECT, true);
        }
    }

    private boolean readMessageCtxProperty(org.apache.synapse.MessageContext msgCtx, String propertyName,
                                           boolean defaultValue) {
        Object propertyObj = msgCtx.getProperty(propertyName);
        if (propertyObj != null) {
            if ((propertyObj instanceof Boolean && ((Boolean) propertyObj))
                    || (propertyObj instanceof String && "true".equals(propertyObj))) {
                return true;
            }
            if (propertyObj instanceof Boolean || propertyObj instanceof String && "false".equals(propertyObj)) {
                return false;
            }
        }
        return defaultValue;
    }

    /**
     * Create the initial message context for rabbitmq
     */
    private org.apache.synapse.MessageContext createMessageContext() {
        org.apache.synapse.MessageContext msgCtx = synapseEnvironment
                .createMessageContext();
        MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx)
                .getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUIDGenerator.getUUID());
        msgCtx.setProperty(MessageContext.CLIENT_API_NON_BLOCKING, true);
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        axis2MsgCtx.setProperty(MultitenantConstants.TENANT_DOMAIN, carbonContext.getTenantDomain());
        return msgCtx;
    }

}
