package com.finemp.finance.withholding;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WithholdingJsonDecoder {
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxNumberLength(20).maxStringLength(4000).maxNameLength(64).maxTokenCount(80).maxDocumentLength(65536).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public Request decode(byte[] body,WithholdingAction action) {
        try {
            if(body==null || body.length==0 || body.length>65536)throw invalid();var root=json.readTree(body);
            fields(root,action==WithholdingAction.CREATED?Set.of("entryVersion","values"):action==WithholdingAction.UPDATED?Set.of("entryVersion","expectedVersion","values"):Set.of("entryVersion","expectedVersion"));
            long parent=version(root,"entryVersion");Long version=action==WithholdingAction.CREATED?null:version(root,"expectedVersion");WithholdingValues values=null;
            if(action!=WithholdingAction.DELETED) {
                var node=root.get("values");fields(node,Set.of("taxCode","treatment","amount","documentReference"));
                var amount=text(node,"amount",true);if(!amount.matches("[0-9]{1,17}([.][0-9]{1,2})?"))throw invalid();
                values=new WithholdingValues(text(node,"taxCode",true),WithholdingTreatment.valueOf(text(node,"treatment",true)),new BigDecimal(amount),text(node,"documentReference",false));
            }
            return new Request(parent,version,values);
        }catch(RuntimeException invalid) {throw invalid();}
    }
    private void fields(JsonNode node,Set<String> allowed) {if(node==null || !node.isObject())throw invalid();for(var p:node.properties())if(!allowed.contains(p.getKey()))throw invalid();}
    private long version(JsonNode node,String name) {var value=node.get(name);if(value==null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<0)throw invalid();return value.longValue();}
    private String text(JsonNode node,String name,boolean required) {var value=node.get(name);if(value==null || value.isNull()){if(required)throw invalid();return "";}if(!value.isTextual())throw invalid();return value.asString();}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Corpo de retenção inválido.");}
    public record Request(Long entryVersion,Long expectedVersion,WithholdingValues values) {@Override public String toString(){return "WithholdingRequest[redacted]";}}
}
