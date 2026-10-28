package com.finemp.connector.agent;

import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.accounting.sync.publication.CanonicalBatchValidator;
import com.finemp.connector.completion.ConnectorWorkResult;
import com.finemp.connector.transport.ConnectorSessionController.ConnectorSession;
import com.finemp.connector.work.*;
import java.io.*;
import java.time.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import static com.finemp.connector.agent.ConnectorClientException.Kind.*;

/** Bounded metadata decoding and exact canonical encoding; no financial classification rules. */
final class ConnectorProtocolCodec {
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096)
                    .maxNameLength(64).maxNumberLength(32).maxTokenCount(1024).maxDocumentLength(65536).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final int maxRecords;
    ConnectorProtocolCodec(int maxRecords) {
        if (maxRecords < 1 || maxRecords > 100000) throw error(CONFIGURATION);
        this.maxRecords = maxRecords;
    }
    ConnectorSession session(byte[] body, UUID source) {
        return decode(body, root -> {
            Long version=count(root,"credentialVersion");if(version==null)throw error(PROTOCOL);
            var result = new ConnectorSession(uuid(root,"credentialId"), uuid(root,"accountingFirmId"), uuid(root,"accountingSourceId"), version);
            if (!result.accountingSourceId().equals(source)) throw error(PROTOCOL); return result;
        });
    }
    ConnectorWorkResponse work(byte[] body, UUID source) {
        return decode(body, root -> {
            var context = root.get("context"); object(context);
            var request = new AccountingReadRequest(uuid(context,"accountingSourceId"), uuid(context,"companyId"),
                    AccountingSourceType.valueOf(text(context,"sourceType")), text(context,"externalCompanyReference"), text(context,"expectedCompanyCnpj"),
                    LocalDate.parse(text(context,"startDate")), LocalDate.parse(text(context,"endDate")));
            if (!request.accountingSourceId().equals(source)) throw error(PROTOCOL);
            var lease = instant(root,"leaseExpiresAt"); var now = instant(root,"serverTime"); validateLease(lease, now);
            long number = positive(root,"attemptNumber"); if (number > 10) throw error(PROTOCOL);
            return new ConnectorWorkResponse(uuid(root,"attemptId"), uuid(root,"runId"), (int) number,
                    SyncRunMode.valueOf(text(root,"mode")), lease, now, request);
        });
    }
    ConnectorLeaseResponse lease(byte[] body, UUID attempt) {
        return decode(body, root -> {
            var id = uuid(root,"attemptId"); var expires = instant(root,"leaseExpiresAt"); var now = instant(root,"serverTime");
            if (!id.equals(attempt)) throw error(PROTOCOL); validateLease(expires, now);
            return new ConnectorLeaseResponse(id, expires, now);
        });
    }
    ConnectorWorkResult result(byte[] body, ConnectorWorkResponse work) {
        return decode(body, root -> {
            var attemptId = uuid(root,"attemptId"); var runId = uuid(root,"runId"); long number = positive(root,"attemptNumber");
            if (!attemptId.equals(work.attemptId()) || !runId.equals(work.runId()) || number != work.attemptNumber()) throw error(PROTOCOL);
            var current = root.get("currentAttempt"); if (current == null || !current.isBoolean()) throw error(PROTOCOL);
            var attempt = SyncAttemptStatus.valueOf(text(root,"attemptStatus")); var run = SyncRunStatus.valueOf(text(root,"runStatus"));
            var read = count(root,"readCount"); var imported = count(root,"importedCount"); var skipped = count(root,"skippedCount");
            if (attempt == SyncAttemptStatus.SUCCEEDED) {
                if (run != SyncRunStatus.SUCCEEDED || !current.booleanValue() || read == null || imported == null || skipped == null
                        || imported > read || skipped > read - imported) throw error(PROTOCOL);
            } else if (read != null || imported != null || skipped != null) throw error(PROTOCOL);
            return new ConnectorWorkResult(attemptId, runId, (int)number, current.booleanValue(), attempt, run,
                    nullableInstant(root,"leaseExpiresAt"), nullableInstant(root,"nextAttemptAt"), failure(root,"attemptFailureCode"),
                    failure(root,"runFailureCode"), read, imported, skipped);
        });
    }
    byte[] batch(ConnectorWorkResponse work, AccountingSourceSnapshot snapshot) {
        try {
            if (snapshot == null || !snapshot.identity().matches(work.context())) throw error(PROTOCOL);
            // Validate exponent/precision before generation; preserve duplicates for server receipt counts.
            CanonicalBatchValidator.validate(work.context(), snapshot.movements(), maxRecords);
            var output = new LimitedOutput(8 * 1024 * 1024);
            try (var json = mapper.createGenerator(output)) {
                json.writeStartObject(); json.writeName("observedIdentity"); json.writeStartObject();
                json.writeStringProperty("externalReference", snapshot.identity().externalReference()); json.writeStringProperty("cnpj", snapshot.identity().cnpj());
                json.writeEndObject(); json.writeName("movements"); json.writeStartArray();
                for (var row : snapshot.movements()) {
                    json.writeStartObject(); json.writeStringProperty("sourceEntryKey",row.sourceEntryKey());
                    json.writeStringProperty("accountingDate",row.accountingDate().toString()); json.writeStringProperty("externalAccountId",row.externalAccountId());
                    json.writeStringProperty("accountKey",row.accountKey()); json.writeStringProperty("accountDescription",row.accountDescription());
                    json.writeStringProperty("side",row.side().name());
                    var amount = row.amount().signum() == 0 ? java.math.BigDecimal.ZERO : row.amount().stripTrailingZeros();
                    json.writeNumberProperty("amount",amount.setScale(4, java.math.RoundingMode.UNNECESSARY));
                    json.writeStringProperty("history",row.history()); json.writeEndObject();
                }
                json.writeEndArray(); json.writeEndObject();
            }
            return output.toByteArray();
        } catch (ConnectorClientException safe) { throw safe; }
        catch (RuntimeException invalid) { throw error(PROTOCOL); }
    }
    private <T> T decode(byte[] body, java.util.function.Function<JsonNode,T> action) {
        try {
            if (body == null || body.length == 0 || body.length > 65536) throw error(PROTOCOL);
            var root = mapper.readTree(body); object(root); return action.apply(root);
        } catch (ConnectorClientException safe) { throw safe; }
        catch (RuntimeException invalid) { throw error(PROTOCOL); }
    }
    private void object(JsonNode value) { if (value == null || !value.isObject()) throw error(PROTOCOL); }
    private String text(JsonNode root, String key) {
        var node = root.get(key); if (node == null || !node.isTextual()) throw error(PROTOCOL); return node.asString();
    }
    private UUID uuid(JsonNode root, String key) {
        var value = text(root,key); var id = UUID.fromString(value); if (!id.toString().equals(value)) throw error(PROTOCOL); return id;
    }
    private long positive(JsonNode root, String key) { var value = count(root,key); if (value == null || value < 1) throw error(PROTOCOL); return value; }
    private Long count(JsonNode root, String key) {
        var value = root.get(key); if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw error(PROTOCOL); return value.longValue();
    }
    private Instant instant(JsonNode root, String key) { return Instant.parse(text(root,key)); }
    private Instant nullableInstant(JsonNode root, String key) { var node=root.get(key); return node == null || node.isNull() ? null : instant(root,key); }
    private SyncFailureCode failure(JsonNode root, String key) { var node=root.get(key); return node == null || node.isNull() ? null : SyncFailureCode.valueOf(text(root,key)); }
    private void validateLease(Instant expires, Instant now) {
        long millis = Duration.between(now,expires).toMillis(); if (millis < 1 || millis > 86400000) throw error(PROTOCOL);
    }
    private static ConnectorClientException error(ConnectorClientException.Kind kind) { return new ConnectorClientException(kind); }
    private static final class LimitedOutput extends ByteArrayOutputStream {
        private final int limit;
        LimitedOutput(int limit) { this.limit=limit; }
        @Override public synchronized void write(int value) { if (count >= limit) throw error(RESOURCE_LIMIT); super.write(value); }
        @Override public synchronized void write(byte[] value, int offset, int length) {
            if (length > limit-count) throw error(RESOURCE_LIMIT); super.write(value,offset,length);
        }
    }
}
