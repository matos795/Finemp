package com.finemp.finance.reconciliation;

import com.finemp.finance.category.CostBehavior;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StatementReconciliationJsonDecoder {
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxNumberLength(20).maxStringLength(5000).maxNameLength(64).maxTokenCount(80).maxDocumentLength(65536).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public ReconcileInput reconcile(byte[] body){try{
        var root=object(body);allow(root,Set.of("expectedStatementVersion","entryId","expectedEntryVersion"));
        return new ReconcileInput(version(root,"expectedStatementVersion"),uuid(root,"entryId",false),version(root,"expectedEntryVersion"));
    }catch(RuntimeException invalid){throw invalid();}}

    public CreateInput create(byte[] body){try{
        var root=object(body);allow(root,Set.of("expectedStatementVersion","values"));
        var values=root.get("values");if(values==null || !values.isObject())throw invalid();
        allow(values,Set.of("description","documentDate","entryDate","competence","dueDate","categoryId","partnerId","resultCenterId","costBehaviorOverride","paymentMethod","documentNumber","notes"));
        var data=new StatementReconciliationCommandService.CreateEntryData(
                text(values,"description",false),date(values,"documentDate"),date(values,"entryDate"),month(values,"competence"),date(values,"dueDate"),
                uuid(values,"categoryId",false),uuid(values,"partnerId",true),uuid(values,"resultCenterId",true),cost(values,"costBehaviorOverride"),
                text(values,"paymentMethod",true),text(values,"documentNumber",true),text(values,"notes",true));
        return new CreateInput(version(root,"expectedStatementVersion"),data);
    }catch(RuntimeException invalid){throw invalid();}}

    private JsonNode object(byte[] body){
        if(body==null || body.length==0 || body.length>65536)throw invalid();var node=json.readTree(body);if(node==null || !node.isObject())throw invalid();return node;
    }
    private static void allow(JsonNode node,Set<String> names){for(var property:node.properties())if(!names.contains(property.getKey()))throw invalid();}
    private static long version(JsonNode node,String name){var value=node.get(name);if(value==null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<0)throw invalid();return value.longValue();}
    private static String text(JsonNode node,String name,boolean optional){var value=node.get(name);if(value==null || value.isNull()){if(optional)return "";throw invalid();}if(!value.isTextual())throw invalid();return value.textValue();}
    private static UUID uuid(JsonNode node,String name,boolean optional){String value=text(node,name,optional);if(optional && value.isBlank())return null;try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){throw invalid();}}
    private static LocalDate date(JsonNode node,String name){try{return LocalDate.parse(text(node,name,false));}catch(DateTimeException invalid){throw invalid();}}
    private static YearMonth month(JsonNode node,String name){try{return YearMonth.parse(text(node,name,false));}catch(DateTimeException invalid){throw invalid();}}
    private static CostBehavior cost(JsonNode node,String name){String value=text(node,name,true);if(value.isBlank())return null;try{return CostBehavior.valueOf(value);}catch(IllegalArgumentException invalid){throw invalid();}}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Comando de conciliação inválido.");}
    public record ReconcileInput(long expectedStatementVersion,UUID entryId,long expectedEntryVersion){}
    public record CreateInput(long expectedStatementVersion,StatementReconciliationCommandService.CreateEntryData values){}
}
