package com.finemp.finance.reconciliation;

import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StatementIgnoreJsonDecoder {
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxNumberLength(20).maxStringLength(128).maxNameLength(64).maxTokenCount(8).maxDocumentLength(65536).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public long decode(byte[] body){try{
        if(body==null || body.length==0 || body.length>65536)throw invalid();var node=json.readTree(body);if(node==null || !node.isObject())throw invalid();
        for(var property:node.properties())if(!Set.of("expectedVersion").contains(property.getKey()))throw invalid();
        var value=node.get("expectedVersion");if(value==null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<0)throw invalid();return value.longValue();
    }catch(RuntimeException invalid){throw invalid();}}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Comando de extrato inválido.");}
}
