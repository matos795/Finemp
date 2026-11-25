package com.finemp.connector.completion;

import com.finemp.accounting.canonical.v2.CanonicalAccountingBatch;
import com.finemp.connector.protocol.ConnectorCanonicalV2Codec;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
@Transactional(propagation = Propagation.NEVER)
public class ConnectorCanonicalV2Decoder {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final ConnectorCanonicalV2Codec codec;

    public ConnectorCanonicalV2Decoder(
            @Value("\${finemp.sync.publication.max-records:25000}") int maxRecords) {
        this.codec = new ConnectorCanonicalV2Codec(maxRecords);
    }

    public CanonicalAccountingBatch decode(InputStream input) {
        if (input == null) {
            throw new InvalidConnectorBatchException();
        }
        try {
            byte[] payload = input.readNBytes(MAX_BYTES + 1);
            if (payload.length > MAX_BYTES) {
                throw new ConnectorBatchResourceLimitException();
            }
            return codec.decode(payload);
        } catch (ConnectorBatchResourceLimitException | InvalidConnectorBatchException safe) {
            throw safe;
        } catch (IOException unavailable) {
            throw new InvalidConnectorBatchException();
        }
    }
}
