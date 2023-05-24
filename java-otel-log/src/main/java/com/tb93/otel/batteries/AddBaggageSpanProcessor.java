package com.tb93.otel.batteries;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

public class AddBaggageSpanProcessor implements SpanProcessor {
    @Override
    public void onStart(Context context, ReadWriteSpan span) {
        // add baggage to span attributes
        Baggage baggage = Baggage.fromContext(context);
        baggage.forEach(
                (key, value) -> span.setAttribute(
                        // add prefix to key to not override existing attributes
                        "baggage." + key,
                        value.getValue()));
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan arg0) {
        throw new UnsupportedOperationException("Unimplemented method 'onEnd'");
    }
}