package com.clearstream.hydrogen.messagetransform;

import com.clearstream.hydrogen.dataaccess.UmbrellaRepository;
import com.clearstream.hydrogen.database.AncRedexConversion;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.HydrogenMessageException;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.RedexDefinitions;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.RedexMessageConversionService;
import com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans.MessageHeaders;
import com.clearstream.hydrogen.util.Util;
import com.clearstream.ifs.hydrogen.redex.ChangeNotification;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.jms.JMSException;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.List;

@Component
@Slf4j
public class CamelSetup extends RouteBuilder {

    @Autowired
    ConfigurationMBean configuration;

    @Autowired
    UmbrellaRepository umbrellaRepository;

    @Autowired
    private RedexMessageConversionService redexMessageConvertionService;



    @Override
    public void configure() {
        processMessageFromQ(Boolean.FALSE);
    }

    public void processMessageFromQ(Boolean transacted) {
        String deadLetterQueue = getJmsRouteFromJndi(configuration.getMessageInDlQueue(), transacted);

        // Read from incoming Queue and send to test Q.
        from( getJmsRouteFromJndi(configuration.getInQueue(), false))
            .log(LoggingLevel.DEBUG, log, "New message received")
            // Note declarative code so Exception handling needs to be declared first
            // these exceptions are handled and logged by the service already
            .onException(HydrogenMessageException.class)
                .handled(true)
                .useOriginalMessage()
                .log("HYDROGEN MESSAGE EXCEPTION: ${header.CamelFileName}")
                .to(deadLetterQueue)
            .end()
            // exceptions not handled by the service are logged by Camel
            .onException(Exception.class)
                .useOriginalMessage()
                .log("UNEXPECTED EXCEPTION: ${header.CamelFileName}")
                .logStackTrace(true)
                .to(deadLetterQueue)
            .end()
            .process(exchange -> {
                Util.setUserId(RedexDefinitions.REDEX);
                String readableMessage = MessageProcessing.convertBytesMessageToString(exchange.getMessage());
                MessageHeaders messageHeaders=new MessageHeaders(exchange.getMessage().getHeaders());


                ChangeNotification changeNotification = com.clearstream.ifs.hydrogen.util.common.MessageProcessing.unmarshalMessage(
                        readableMessage, ChangeNotification.class);
                redexMessageConvertionService.processNotificationForHydrogen(changeNotification,messageHeaders);

                exchange.getMessage().setBody(readableMessage);
            })
            .end();
    }




    private String getJmsRouteFromJndi(String jndi, boolean transacted) {
        // default, used by integration tests
        String ret = "jms:" + jndi;

        // when running under jBoss, we get queue name here
        // in integration tests, we don't have JNDI context, so this fails with
        // javax.naming.NoInitialContextException
        try {
            StringBuffer physicalQueueName = new StringBuffer("jms:");
            InitialContext initialContext = new InitialContext();
            Object object = initialContext.lookup(jndi);
            // prepended with "queue://" - can't cast to ActiveMQQueue - get class cast
            // exception. can't spot classpath issue. maybe class loader issue
            physicalQueueName.append(object.toString().substring(8));
            ret = physicalQueueName.toString();
        } catch (Exception exception) {
            int currentLine = exception.getStackTrace()[0].getLineNumber();
            log.warn("currentLine: "+ currentLine + exception + " from jndi - " + jndi);
        }

        // When transacted, message is acknowledged only after entire route was
        // processed. If not transacted, message is acknowledged (consumed) as soon as
        // it's read from the queue.
        if (transacted && !ret.contains("transacted")) {
            if (!ret.contains("?")) {
                ret += "?";
            } else {
                ret += "&";
            }
            ret += "transacted=true";
        }

        log.debug("Returning message consumer url: " + ret);
        return ret;
    }

}
