package com.clearstream.hydrogen.messagetransform;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.destination.JndiDestinationResolver;
import org.springframework.jndi.JndiObjectFactoryBean;

import javax.jms.ConnectionFactory;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import java.util.Properties;

/**
 * Auto-configuration for JBoss JNDI provided JMS ConnectionFactory. This only
 * ever activates if org.jboss.naming.remote.client.InitialContextFactory is
 * present in the classpath, i.e. deployed to JBoss. Integration tests on the
 * other hand run their own embedded activemq broker.
 */
@Configuration
@EnableJms
@ConditionalOnClass(name = "org.jboss.naming.remote.client.InitialContextFactory")
public class JBossJmsConfiguration {
  private static final String INITIAL_CONTEXT_FACTORY = "org.wildfly.naming.client.WildFlyInitialContextFactory";

  @Autowired
  ConfigurationMBean configuration;

  @Bean
  public ConnectionFactory connectionFactory() {
    try {
      JndiObjectFactoryBean jndiObjectFactoryBean = new JndiObjectFactoryBean();
      jndiObjectFactoryBean.setJndiName(configuration.getJmsConnectionFactory());

      jndiObjectFactoryBean.setJndiEnvironment(getEnvProperties());
      jndiObjectFactoryBean.afterPropertiesSet();

      return (ConnectionFactory) jndiObjectFactoryBean.getObject();
    } catch (Exception exception) {
      throw new RuntimeException("Failed to fetch JMS Connection factory", exception);
    }
  }

  Properties getEnvProperties() {
    Properties env = new Properties();
    env.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
    return env;
  }

  @Bean
  public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {

    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    JndiDestinationResolver jndiDestinationResolver = new JndiDestinationResolver();

    jndiDestinationResolver.setJndiEnvironment(getEnvProperties());
    factory.setDestinationResolver(jndiDestinationResolver);
    return factory;
  }
}
