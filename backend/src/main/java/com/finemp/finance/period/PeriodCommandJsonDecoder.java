package com.finemp.finance.period;

import java.time.YearMonth;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PeriodCommandJsonDecoder {
    private final JsonMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxNumberLength(20).maxStringLength(1000).maxNameLength(64).maxTokenCount(20).maxDocumentLength(65536).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public Request decode(byte[] body,boolean reopen) {
        try {
            if(body==null || body.length==0 || body.length>65536)throw invalid();var root=mapper.readTree(body);
            var allowed=reopen?Set.of("expectedVersion","reason"):Set.of("expectedVersion");
            if(root==null || !root.isObject() || !root.has("expectedVersion"))throw invalid();
            for(var property:root.properties())if(!allowed.contains(property.getKey()))throw invalid();
            var number=root.get("expectedVersion");Long version=null;
            if(!number.isNull()) {if(!number.isIntegralNumber() || !number.canConvertToLong() || number.longValue()<0)throw invalid();version=number.longValue();}
            String reason="";
            if(reopen) {var text=root.get("reason");if(version==null || text==null || !text.isTextual() || text.asString().isBlank())throw invalid();reason=text.asString();}
            return new Request(version,reason);
        }catch(RuntimeException invalid) {throw invalid();}
    }
    public static YearMonth period(String value) {
        if(value==null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid();return FinancialPeriodGuard.valid(YearMonth.parse(value));
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Comando de período financeiro inválido.");}
    public record Request(Long expectedVersion,String reason) {@Override public String toString() {return "PeriodRequest[redacted]";}}
}
