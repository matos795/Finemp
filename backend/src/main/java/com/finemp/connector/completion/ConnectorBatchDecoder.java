package com.finemp.connector.completion;

import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.sync.SyncFailureCode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;

@Component
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
@Transactional(propagation = Propagation.NEVER)
public class ConnectorBatchDecoder {
    private static final Set<String> REQUIRED_MOVEMENT = Set.of("sourceEntryKey", "accountingDate", "externalAccountId",
            "accountKey", "accountDescription", "side", "amount");
    private final int maxRecords;
    private final JsonFactory factory;
    public ConnectorBatchDecoder(@Value("${finemp.sync.publication.max-records:25000}") int maxRecords) {
        if (maxRecords < 1 || maxRecords > 100000) throw new IllegalArgumentException("Invalid connector record budget.");
        this.maxRecords = maxRecords;
        factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxNumberLength(64)
                        .maxStringLength(4096).maxNameLength(64).maxDocumentLength(16L * 1024 * 1024)
                        .maxTokenCount(24L * maxRecords + 128).build()).build();
    }
    public ConnectorBatch decode(InputStream input) {
        if (input == null) throw invalid();
        try (var parser = factory.createParser(ObjectReadContext.empty(), input)) {
            require(parser.nextToken(), JsonToken.START_OBJECT);
            var seen = new HashSet<String>(); ExternalCompanyIdentity identity = null; List<ImportedAccountingMovement> rows = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken(), JsonToken.PROPERTY_NAME); String field = parser.currentName();
                if (!seen.add(field)) throw invalid(); parser.nextToken();
                switch (field) {
                    case "observedIdentity" -> identity = identity(parser);
                    case "movements" -> rows = movements(parser);
                    default -> throw invalid();
                }
            }
            if (identity == null || rows == null || parser.nextToken() != null) throw invalid();
            return new ConnectorBatch(identity, rows);
        } catch (StreamConstraintsException resourceLimit) { throw new ConnectorBatchResourceLimitException(); }
        catch (JacksonException | DateTimeException | IllegalArgumentException invalid) { throw invalid(); }
    }
    public SyncFailureCode decodeFailure(InputStream input) {
        if (input == null) throw invalid();
        try (var parser = factory.createParser(ObjectReadContext.empty(), input)) {
            require(parser.nextToken(), JsonToken.START_OBJECT); require(parser.nextToken(), JsonToken.PROPERTY_NAME);
            if (!"code".equals(parser.currentName())) throw invalid(); parser.nextToken();
            var code = SyncFailureCode.valueOf(string(parser));
            if (code != SyncFailureCode.READ_FAILED && code != SyncFailureCode.RESOURCE_LIMIT
                    && code != SyncFailureCode.IDENTITY_MISMATCH && code != SyncFailureCode.INVALID_PAYLOAD) throw invalid();
            require(parser.nextToken(), JsonToken.END_OBJECT);
            if (parser.nextToken() != null) throw invalid();
            return code;
        } catch (StreamConstraintsException resourceLimit) { throw new ConnectorBatchResourceLimitException(); }
        catch (JacksonException | IllegalArgumentException invalid) { throw invalid(); }
    }
    private ExternalCompanyIdentity identity(JsonParser parser) {
        require(parser.currentToken(), JsonToken.START_OBJECT);
        String reference = null, cnpj = null; var seen = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken(), JsonToken.PROPERTY_NAME); String field = parser.currentName();
            if (!seen.add(field)) throw invalid(); parser.nextToken();
            switch (field) {
                case "externalReference" -> reference = string(parser);
                case "cnpj" -> cnpj = string(parser);
                default -> throw invalid();
            }
        }
        if (reference == null || cnpj == null) throw invalid();
        return new ExternalCompanyIdentity(reference, cnpj);
    }
    private List<ImportedAccountingMovement> movements(JsonParser parser) {
        require(parser.currentToken(), JsonToken.START_ARRAY); var rows = new ArrayList<ImportedAccountingMovement>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (rows.size() >= maxRecords) throw new ConnectorBatchResourceLimitException();
            rows.add(movement(parser));
        }
        return rows;
    }
    private ImportedAccountingMovement movement(JsonParser parser) {
        require(parser.currentToken(), JsonToken.START_OBJECT);
        String entry = null, externalId = null, key = null, description = null, history = null;
        LocalDate date = null; AccountingMovementSide side = null; BigDecimal amount = null; var seen = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken(), JsonToken.PROPERTY_NAME); String field = parser.currentName();
            if (!seen.add(field)) throw invalid(); parser.nextToken();
            switch (field) {
                case "sourceEntryKey" -> entry = string(parser);
                case "accountingDate" -> date = LocalDate.parse(string(parser));
                case "externalAccountId" -> externalId = string(parser);
                case "accountKey" -> key = string(parser);
                case "accountDescription" -> description = string(parser);
                case "side" -> side = AccountingMovementSide.valueOf(string(parser));
                case "amount" -> amount = decimal(parser);
                case "history" -> history = parser.currentToken() == JsonToken.VALUE_NULL ? null : string(parser);
                default -> throw invalid();
            }
        }
        if (!seen.containsAll(REQUIRED_MOVEMENT)) throw invalid();
        return new ImportedAccountingMovement(entry, date, externalId, key, description, side, amount, history);
    }
    private BigDecimal decimal(JsonParser parser) {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String value = parser.getString();
            if (value.length() > 64) throw new ConnectorBatchResourceLimitException();
            return new BigDecimal(value);
        }
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) throw invalid();
        return parser.getDecimalValue();
    }
    private String string(JsonParser parser) { require(parser.currentToken(), JsonToken.VALUE_STRING); return parser.getString(); }
    private void require(JsonToken actual, JsonToken expected) { if (actual != expected) throw invalid(); }
    private InvalidConnectorBatchException invalid() { return new InvalidConnectorBatchException(); }
}
