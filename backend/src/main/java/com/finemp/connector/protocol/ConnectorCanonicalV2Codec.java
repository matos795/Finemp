package com.finemp.connector.protocol;

import com.finemp.accounting.canonical.v2.CanonicalAccountingBatch;
import com.finemp.connector.completion.ConnectorBatchResourceLimitException;
import com.finemp.connector.completion.InvalidConnectorBatchException;
import java.io.ByteArrayOutputStream;
import tools.jackson.core.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class ConnectorCanonicalV2Codec {
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private final int maxRecords;
    private final JsonMapper mapper;

    public ConnectorCanonicalV2Codec(int maxRecords) {
        if (maxRecords < 1 || maxRecords > 100000) {
            throw new IllegalArgumentException("Invalid connector v2 record budget.");
        }
        this.maxRecords = maxRecords;
        this.mapper = JsonMapper.builder(
                        JsonFactory.builder()
                                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                                .streamReadConstraints(StreamReadConstraints.builder()
                                        .maxNestingDepth(12)
                                        .maxNumberLength(64)
                                        .maxStringLength(4096)
                                        .maxNameLength(64)
                                        .maxDocumentLength(MAX_BYTES)
                                        .maxTokenCount(64L * maxRecords + 2048)
                                        .build())
                                .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public byte[] encode(CanonicalAccountingBatch batch) {
        if (batch == null || batch.contractVersion() != CanonicalAccountingBatch.VERSION) {
            throw invalid();
        }
        enforceRecordBudget(batch);
        try {
            var output = new LimitedOutput(MAX_BYTES);
            mapper.writeValue(output, batch);
            return output.toByteArray();
        } catch (ConnectorBatchResourceLimitException limited) {
            throw limited;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    public CanonicalAccountingBatch decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw invalid();
        }
        if (payload.length > MAX_BYTES) {
            throw new ConnectorBatchResourceLimitException();
        }
        try {
            var batch = mapper.readValue(payload, CanonicalAccountingBatch.class);
            if (batch == null || batch.contractVersion() != CanonicalAccountingBatch.VERSION) {
                throw invalid();
            }
            enforceRecordBudget(batch);
            return batch;
        } catch (ConnectorBatchResourceLimitException limited) {
            throw limited;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private void enforceRecordBudget(CanonicalAccountingBatch batch) {
        long records = 0;
        records = add(records, batch.chart() == null || batch.chart().accounts() == null ? 0 : batch.chart().accounts().size());
        records = add(records, batch.observedAccountDescriptors() == null ? 0 : batch.observedAccountDescriptors().size());
        if (batch.entries() != null) {
            records = add(records, batch.entries().entries() == null ? 0 : batch.entries().entries().size());
            records = add(records, batch.entries().metadata() == null ? 0 : batch.entries().metadata().size());
        }
        records = add(records, batch.dailyTotals() == null ? 0 : batch.dailyTotals().size());
        records = add(records, batch.monthlyTotals() == null ? 0 : batch.monthlyTotals().size());
        records = add(records, batch.originCatalog() == null ? 0 : batch.originCatalog().size());
        records = add(records, batch.participants() == null ? 0 : batch.participants().size());
        records = add(records, batch.costAllocations() == null ? 0 : batch.costAllocations().size());
        if (records > maxRecords) {
            throw new ConnectorBatchResourceLimitException();
        }
    }

    private long add(long current, int value) {
        long next = current + value;
        if (next < current || next > maxRecords) {
            throw new ConnectorBatchResourceLimitException();
        }
        return next;
    }

    private InvalidConnectorBatchException invalid() {
        return new InvalidConnectorBatchException();
    }

    private static final class LimitedOutput extends ByteArrayOutputStream {
        private final int limit;

        LimitedOutput(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(int value) {
            if (count >= limit) {
                throw new ConnectorBatchResourceLimitException();
            }
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] value, int offset, int length) {
            if (length > limit - count) {
                throw new ConnectorBatchResourceLimitException();
            }
            super.write(value, offset, length);
        }
    }
}
