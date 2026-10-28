package com.finemp.finance.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BankTransferCommandJsonDecoder {
    private final JsonMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(3).maxNumberLength(20).maxStringLength(4000).maxNameLength(64)
                    .maxTokenCount(40).maxDocumentLength(65536).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public BankTransferValues decode(byte[] body) {
        try {
            if(body==null || body.length==0 || body.length>65536)throw invalid();var root=mapper.readTree(body);fields(root,Set.of("values"));
            var values=root.get("values");fields(values,Set.of("originBankAccountId","destinationBankAccountId","transferDate","amount","description"));
            var money=text(values,"amount",true);if(!money.matches("[0-9]{1,17}([.][0-9]{1,2})?"))throw invalid();var amount=new BigDecimal(money);if(amount.signum()<=0)throw invalid();
            var dateText=text(values,"transferDate",true);var date=LocalDate.parse(dateText);if(!date.toString().equals(dateText))throw invalid();
            return new BankTransferValues(uuid(values,"originBankAccountId"),uuid(values,"destinationBankAccountId"),date,amount,text(values,"description",false));
        }catch(RuntimeException invalid) {throw invalid();}
    }
    private void fields(JsonNode node,Set<String> allowed) {
        if(node==null || !node.isObject())throw invalid();for(var property:node.properties())if(!allowed.contains(property.getKey()))throw invalid();
    }
    private String text(JsonNode node,String field,boolean required) {
        var value=node.get(field);if(value==null || value.isNull()) {if(required)throw invalid();return "";}
        if(!value.isTextual())throw invalid();return value.asString();
    }
    private UUID uuid(JsonNode values,String field) {var text=text(values,field,true);var id=UUID.fromString(text);if(!id.toString().equalsIgnoreCase(text))throw invalid();return id;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Corpo do comando de transferência inválido.");}
}
