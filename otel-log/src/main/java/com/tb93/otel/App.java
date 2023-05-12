package com.tb93.otel;

import org.slf4j.LoggerFactory;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

public class App {
        private static final String SERVICE_NAME = System.getenv("OTEL_SERVICE_NAME");

        private static final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(SERVICE_NAME);

        public static void main(String[] args) {

                try {
                        // Initialize OpenTelemetry as early as possible
                        initializeOpenTelemetry();

                        while (true) {
                                Thread.sleep(10 * 1000);

                                // Log using slf4j API w/ logback backend
                                maybeRunWithSpan(() -> slf4jLogger.info("A slf4j log message without a span"), false);
                                maybeRunWithSpan(() -> slf4jLogger.info("A slf4j log message with a span"), true);
                                maybeRunWithSpan(() -> slf4jLogger.info("A slf4j log message with a span and event"), true, true);
                                maybeRunWithSpan(
                                                () -> slf4jLogger
                                                                .atInfo()
                                                                .setMessage("A slf4j structured message")
                                                                .addKeyValue("key", "value")
                                                                .log(),
                                                false);
                                maybeRunWithSpan(
                                                () -> slf4jLogger.info("A slf4j log message with an exception",
                                                                new Exception("error!")),
                                                false);

                        }
                } catch (Exception e) {

                }
        }

        private static void initializeOpenTelemetry() {

                Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)));

                SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                .setSampler(Sampler.alwaysOn())
                                .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder()
                                                .setEndpoint(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).build())
                                                .build())
                                .setResource(resource)
                                .build();

                SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                                .setResource(resource)
                                .addLogRecordProcessor(
                                                BatchLogRecordProcessor.builder(
                                                                OtlpGrpcLogRecordExporter.builder()
                                                                                .setEndpoint(System.getenv(
                                                                                                "OTEL_EXPORTER_OTLP_ENDPOINT"))
                                                                                .build())
                                                                .build())
                                .build();

                OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                                .setTracerProvider(sdkTracerProvider)
                                .setLoggerProvider(sdkLoggerProvider)
                                .build();
                GlobalOpenTelemetry.set(sdk);
                GlobalLoggerProvider.set(sdk.getSdkLoggerProvider());

                // Add hook to close SDK, which flushes logs
                Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
        }

        private static void maybeRunWithSpan(Runnable runnable, boolean withSpan) {
                maybeRunWithSpan(runnable, withSpan, false);
        }

        private static void maybeRunWithSpan(Runnable runnable, boolean withSpan, boolean withEvent) {
                if (!withSpan) {
                        runnable.run();
                        return;
                }
                Span span = GlobalOpenTelemetry.getTracer("my-tracer").spanBuilder("my-span").startSpan();
                try (Scope unused = span.makeCurrent()) {
                        runnable.run();
                        if (withEvent)
                                span.addEvent("a span event");
                } finally {
                        span.end();
                }
        }
}
