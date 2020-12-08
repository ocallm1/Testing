package com.clearstream.hydrogen.messagetransform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.stereotype.Component;

@Component("configuration")
public class ConfigurationMBean {

    @Value("${messagetransformationengine.maximumRedeliveries}")
    private Integer maximumRedeliveries;

    @Value("${messagetransformationengine.redeliveryDelay}")
    private Long redeliveryDelay;

    @Value("${redex.input.queue}")
    private String inQueue;

    @Value("${redex.output.queue}")
    private String outQueue;

    @Value("${hydrogen.jmsConnectionFactory:embedded}")
    private String jmsConnectionFactory;


    @Value("${redex.input.DLqueue}")
    private String messageInDlQueue;

    @ManagedAttribute
    public String getInQueue() {
        return inQueue;
    }

    @ManagedAttribute
    public void setInQueue(String inQueue) {
        this.inQueue = inQueue;
    }

    @ManagedAttribute
    public String getOutQueue() {
        return outQueue;
    }

    @ManagedAttribute
    public void setOutQueue(String outQueue) {
        this.outQueue = outQueue;
    }

    @ManagedAttribute
    public String getJmsConnectionFactory() {
        return jmsConnectionFactory;
    }

    @ManagedAttribute
    public void setJmsConnectionFactory(String jmsConnectionFactory) {
        this.jmsConnectionFactory = jmsConnectionFactory;
    }

    @ManagedAttribute
    public String getMessageInDlQueue() {
        return messageInDlQueue;
    }

    @ManagedAttribute
    public Integer getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @ManagedAttribute
    public void setMaximumRedeliveries(Integer maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @ManagedAttribute
    public Long getRedeliveryDelay() {
        return redeliveryDelay;
    }

    @ManagedAttribute
    public void setRedeliveryDelay(Long redeliveryDelay) {
        this.redeliveryDelay = redeliveryDelay;
    }
}
