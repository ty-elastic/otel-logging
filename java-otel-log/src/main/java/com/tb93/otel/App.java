package com.tb93.otel;

import java.util.random.RandomGenerator;

import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import com.tb93.otel.batteries.AddBaggageLogProcessor;
import com.tb93.otel.batteries.AddBaggageSpanProcessor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.api.baggage.Baggage;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;

public class App {
        private static final String SERVICE_NAME = System.getenv("OTEL_SERVICE_NAME");

        private static final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(SERVICE_NAME);

        public static void main(String[] args) {
                System.out.println("Startup");

                try {
                        // Initialize OpenTelemetry as early as possible
                        initializeOpenTelemetry();

                        while (true) {
                                try {
                                        Thread.sleep(10 * 1000);

                                        Baggage.current().toBuilder()
                                                        .put("session_id", Long
                                                                        .toString(RandomGenerator.getDefault()
                                                                                        .nextLong()))
                                                        .build().makeCurrent();

                                        Attributes attributes = Attributes.of(AttributeKey.stringKey("user_id"),
                                                        Long.toString(RandomGenerator.getDefault().nextLong()));

                                        doFunction("log message without span", false, false, false, false,
                                                        null);
                                        doFunction("log message with span", false, true, false, false,
                                                        attributes);
                                        doFunction("log message with span and event", false, true, false,
                                                        true, attributes);
                                        doFunction("log message with span and event and key/value", true, true, false,
                                                        true, attributes);
                                        doFunction("log message with key/value", true, false, false, false,
                                                        null);
                                        doFunction("log message with exception", false, false, true, false,
                                                        null);
                                        doFunction("log message with span and exception", false, true, true,
                                                        false, attributes);

                                } catch (Exception e) {
                                        System.out.println(e);
                                }

                        }
                } catch (Exception e) {
                        System.out.println(e);
                }
        }

        private static void initializeOpenTelemetry() {

                Resource resource = Resource.getDefault().merge(Resource.create(
                                Attributes.of(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)));

                SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                .setResource(resource).setSampler(Sampler.alwaysOn())
                                .addSpanProcessor(new AddBaggageSpanProcessor())
                                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter
                                                .builder()
                                                .setEndpoint(System.getenv(
                                                                "OTEL_EXPORTER_OTLP_ENDPOINT"))
                                                .build()).build())
                                .build();

                SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                                .setResource(resource)
                                .addLogRecordProcessor(new AddBaggageLogProcessor())
                                .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                                                OtlpGrpcLogRecordExporter.builder().setEndpoint(
                                                                System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
                                                                .build())
                                                .build())
                                .build();

                SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().setResource(resource)
                                .registerMetricReader(PeriodicMetricReader.builder(
                                                OtlpGrpcMetricExporter.builder().setEndpoint(System
                                                                .getenv("OTEL_EXPORTER_OTLP_ENDPOINT"))
                                                                .build())
                                                .build())
                                .build();

                OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                                .setTracerProvider(sdkTracerProvider)
                                .setLoggerProvider(sdkLoggerProvider)
                                .setMeterProvider(sdkMeterProvider)
                                .setPropagators(ContextPropagators
                                                .create(W3CTraceContextPropagator.getInstance()))
                                .build();
                GlobalOpenTelemetry.set(sdk);
                GlobalLoggerProvider.set(sdk.getSdkLoggerProvider());

                // Gets or creates a named meter instance
                Meter meter = sdk.meterBuilder(SERVICE_NAME).build();

                // Build counter e.g. LongCounter
                requestCount = meter.counterBuilder("request_count").setDescription("Requests")
                                .setUnit("1").build();

                // Add hook to close SDK, which flushes logs
                Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        }

        static LongCounter requestCount;

        public static Attributes makeAttributesFromBaggage(Context context) {
                Baggage baggage = Baggage.fromContext(context);
                AttributesBuilder attributesBuilder = Attributes.builder();
                baggage.forEach((key, value) -> attributesBuilder.put(
                                // add prefix to key to not override existing attributes
                                "baggage." + key, value.getValue()));

                return attributesBuilder.build();
        }

        private static void doFunction(String message, boolean withKeyValue, boolean withSpan,
                        boolean withException, boolean withSpanEvent, Attributes spanAttribute) {

                Span span = null;
                Scope scope = null;
                if (withSpan) {
                        span = GlobalOpenTelemetry.getTracer(SERVICE_NAME).spanBuilder("func").startSpan();
                        if (spanAttribute != null)
                                span.setAllAttributes(spanAttribute);
                        scope = span.makeCurrent();
                }

                LoggingEventBuilder log;
                Throwable ex = new Exception("error!");
                if (withException)
                        log = slf4jLogger.atWarn().addArgument(ex);
                else
                        log = slf4jLogger.atInfo();
                log = log.setMessage(message);
                if (withKeyValue) {
                        log = log.addKeyValue("someKey", Long.valueOf(93));
                }
                log.log();

                requestCount.add(1, makeAttributesFromBaggage(Context.current()));

                if (withSpan) {
                        try {
                                if (withSpanEvent) {
                                        if (withKeyValue)
                                                span.addEvent("a span event", Attributes
                                                                .of(AttributeKey.longKey("someKey"), Long.valueOf(93)));
                                        else
                                                span.addEvent("a span event");
                                }
                                if (withException)
                                        throw ex;
                                span.setStatus(StatusCode.OK);
                        } catch (Throwable t) {
                                span.setStatus(StatusCode.ERROR);
                                span.recordException(t);
                        } finally {
                                span.end();
                        }
                        scope.close();
                }
        }
}
