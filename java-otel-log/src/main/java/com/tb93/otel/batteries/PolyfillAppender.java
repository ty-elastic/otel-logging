package com.tb93.otel.batteries;

/*
 * Polyfill for various Java OTel logging methods
 * Based heavlity and dependent upon io.opentelemetry.instrumentation.logback.mdc.v1_0
 * which is Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.logback.mdc.v1_0.internal.UnionMap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

public class PolyfillAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent> {
  private boolean addKeyValuePairsAsMDC;
  private boolean addKeyValuePairsAsStructured;

  private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();

  /**
   * When set to true this will enable addition of all KeyValue entries to MDC.
   * This can be done by
   * adding the following to the logback.xml config for this appender. {@code
   * <setAddKeyValuePairsAsMDC>true</setAddKeyValuePairsAsMDC>}
   *
   * @param addKeyValuePairsAsMDC True if KeyValue pairs should be added to MDC
   */
  public void setAddKeyValuePairsAsMDC(boolean addKeyValuePairsAsMDC) {
    this.addKeyValuePairsAsMDC = addKeyValuePairsAsMDC;
  }

  /**
   * When set to true this will enable addition of all KeyValue entries to structured data.
   * This can be done by
   * adding the following to the logback.xml config for this appender. {@code
   * <addKeyValuePairsAsStructured>true</addKeyValuePairsAsStructured>}
   *
   * @param addKeyValuePairsAsStructured True if KeyValue pairs should be added to
   *                                     MDC
   */
  public void setAddKeyValuePairsAsStructured(boolean addKeyValuePairsAsStructured) {
    this.addKeyValuePairsAsStructured = addKeyValuePairsAsStructured;
  }

  public ILoggingEvent wrapEvent(ILoggingEvent event) {
    Map<String, String> eventContext = event.getMDCPropertyMap();
    Map<String, String> contextData = new HashMap<>();

    // add slf4j keyvalue pairs to MDC (note that MDC is always string value)
    if (addKeyValuePairsAsMDC) {
      List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
      if (keyValuePairs != null) {
        keyValuePairs.forEach(
            (keyValuePair) -> contextData.put(
                keyValuePair.key, keyValuePair.value.toString()));
      }
    }

    // convert slf4j keyvalue pairs to "structured data"
    List<Object> eventArguments = new ArrayList<Object>();
    if (event.getArgumentArray() != null) {
      eventArguments.addAll(Arrays.asList(event.getArgumentArray()));
    }
    if (addKeyValuePairsAsStructured) {
      List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
      if (keyValuePairs != null) {
        keyValuePairs.forEach(
            (keyValuePair) -> eventArguments.add(
                keyValue(keyValuePair.key, keyValuePair.value)));
      }
    }

    if (eventContext == null) {
      eventContext = contextData;
    } else {
      eventContext = new UnionMap<>(eventContext, contextData);
    }
    Map<String, String> eventContextMap = eventContext;
    LoggerContextVO oldVo = event.getLoggerContextVO();
    // capture caller data here
    StackTraceElement[] oldCallerData = event.getCallerData();
    LoggerContextVO vo = oldVo != null
        ? new LoggerContextVO(oldVo.getName(), eventContextMap, oldVo.getBirthTime())
        : null;

    // wrap old event and override specific methods to augment data returned
    ILoggingEvent wrappedEvent = (ILoggingEvent) Proxy.newProxyInstance(
        ILoggingEvent.class.getClassLoader(),
        new Class<?>[] { ILoggingEvent.class },
        (proxy, method, args) -> {
          if ("getArgumentArray".equals(method.getName())) {
            return eventArguments.toArray();
          } else if ("getCallerData".equals(method.getName())) {
            return oldCallerData;
          } else if ("getMDCPropertyMap".equals(method.getName())) {
            return eventContextMap;
          } else if ("getLoggerContextVO".equals(method.getName())) {
            return vo;
          }
          try {
            return method.invoke(event, args);
          } catch (Exception exception) {
            System.out.println(exception);
            throw exception.getCause();
          }
        });
    // https://github.com/qos-ch/logback/blob/9e833ec858953a2296afdc3292f8542fc08f2a45/logback-classic/src/main/java/ch/qos/logback/classic/net/LoggingEventPreSerializationTransformer.java#L29
    // LoggingEventPreSerializationTransformer accepts only subclasses of LoggingEvent and
    // LoggingEventVO, here we transform our wrapped event into a LoggingEventVO
    return LoggingEventVO.build(wrappedEvent);
  }

  @Override
  protected void append(ILoggingEvent event) {
    aai.appendLoopOnAppenders(wrapEvent(event));
  }

  @Override
  public void addAppender(Appender<ILoggingEvent> appender) {
    aai.addAppender(appender);
  }

  @Override
  public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
    return aai.iteratorForAppenders();
  }

  @Override
  public Appender<ILoggingEvent> getAppender(String name) {
    return aai.getAppender(name);
  }

  @Override
  public boolean isAttached(Appender<ILoggingEvent> appender) {
    return aai.isAttached(appender);
  }

  @Override
  public void detachAndStopAllAppenders() {
    aai.detachAndStopAllAppenders();
  }

  @Override
  public boolean detachAppender(Appender<ILoggingEvent> appender) {
    return aai.detachAppender(appender);
  }

  @Override
  public boolean detachAppender(String name) {
    return aai.detachAppender(name);
  }
}
