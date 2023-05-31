package com.tb93.otel.batteries;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

public class AddBaggageLogProcessor implements LogRecordProcessor {

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        // add baggage to log attributes
        Baggage baggage = Baggage.fromContext(context);
        baggage.forEach(
                (key, value) -> logRecord.setAttribute(
                        // add prefix to key to not override existing attributes
                        AttributeKey.stringKey("baggage." + key),
                        value.getValue()));
    }
}