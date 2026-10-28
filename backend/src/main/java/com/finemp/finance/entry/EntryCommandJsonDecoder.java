package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict, bounded canonical input. Never coerce binary JSON numbers into monetary text. */
@Component
public class EntryCommandJsonDecoder {
    private static final Set<String> FIELDS=Set.of("direction","description","nominalAmount","documentDate","entryDate","competence","dueDate","paymentDate",
            "bankAccountId","categoryId","partnerId","resultCenterId","costBehaviorOverride","paymentMethod","documentNumber","notes");
    private final JsonMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxNumberLength(20).maxStringLength(4000).maxNameLength(64)
                    .maxTokenCount(128).maxDocumentLength(65536).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public Request decode(byte[] body) {
        try {
            var root=root(body);fields(root,Set.of("values","expectedVersion"));var values=root.get("values");fields(values,FIELDS);
            var data=new FinancialEntryCommandData(FinancialDirection.valueOf(text(values,"direction",true)),text(values,"description",true),money(values,"nominalAmount"),
                    date(values,"documentDate",true),date(values,"entryDate",true),YearMonth.parse(text(values,"competence",true)),date(values,"dueDate",true),date(values,"paymentDate",false),
                    uuid(values,"bankAccountId",true),uuid(values,"categoryId",true),uuid(values,"partnerId",false),uuid(values,"resultCenterId",false),
                    nullable(values,"costBehaviorOverride")?null:CostBehavior.valueOf(text(values,"costBehaviorOverride",true)),
                    text(values,"paymentMethod",false),text(values,"documentNumber",false),text(values,"notes",false));
            return new Request(data,version(root,false));
        }catch(RuntimeException invalid) {throw invalid();}
    }
    public SettlementRequest decodeSettlement(byte[] body) {
        try {
            var root=root(body);fields(root,Set.of("values","expectedVersion"));var values=root.get("values");
            fields(values,Set.of("paymentDate","paidAmount","bankAccountId"));
            var data=new EntrySettlementData(date(values,"paymentDate",true),money(values,"paidAmount"),uuid(values,"bankAccountId",true));
            return new SettlementRequest(data,version(root,true));
        }catch(RuntimeException invalid) {throw invalid();}
    }
    public Long decodeReversal(byte[] body) {
        try {var root=root(body);fields(root,Set.of("expectedVersion"));return version(root,true);}
        catch(RuntimeException invalid) {throw invalid();}
    }
    private JsonNode root(byte[] body) {
        if(body==null || body.length==0 || body.length>65536)throw invalid();return mapper.readTree(body);
    }
    private BigDecimal money(JsonNode root,String key) {
        var value=text(root,key,true);if(!value.matches("[0-9]{1,17}(\\.[0-9]{1,2})?"))throw invalid();return new BigDecimal(value);
    }
    private Long version(JsonNode root,boolean required) {
        if(nullable(root,"expectedVersion")) {if(required)throw invalid();return null;}
        var node=root.get("expectedVersion");if(!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue()<0)throw invalid();return node.longValue();
    }
    private void fields(JsonNode node,Set<String> allowed) {
        if(node==null || !node.isObject())throw invalid();
        for(var property:node.properties())if(!allowed.contains(property.getKey()))throw invalid();
    }
    private boolean nullable(JsonNode root,String key) {var value=root.get(key);return value==null || value.isNull();}
    private String text(JsonNode root,String key,boolean required) {
        if(nullable(root,key)) {if(required)throw invalid();return "";}
        var node=root.get(key);if(!node.isTextual())throw invalid();String value=node.asString();
        for(int index=0;index<value.length();index++) {
            char current=value.charAt(index);if(current==0 || Character.isLowSurrogate(current))throw invalid();
            if(Character.isHighSurrogate(current) && (++index>=value.length() || !Character.isLowSurrogate(value.charAt(index))))throw invalid();
        }
        return value;
    }
    private UUID uuid(JsonNode root,String key,boolean required) {
        if(!required && nullable(root,key))return null;
        String text=text(root,key,true);var id=UUID.fromString(text);if(!id.toString().equalsIgnoreCase(text))throw invalid();return id;
    }
    private LocalDate date(JsonNode root,String key,boolean required) {return !required && nullable(root,key)?null:LocalDate.parse(text(root,key,true));}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Corpo do comando financeiro inválido.");}
    public record Request(FinancialEntryCommandData values,Long expectedVersion) {
        @Override public String toString() {return "FinancialEntryCommandRequest[redacted]";}
    }
    public record SettlementRequest(EntrySettlementData values,Long expectedVersion) {
        @Override public String toString() {return "EntrySettlementRequest[redacted]";}
    }
}
