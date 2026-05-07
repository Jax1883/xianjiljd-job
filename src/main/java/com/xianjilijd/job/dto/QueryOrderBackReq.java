package com.xianjilijd.job.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class QueryOrderBackReq {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @JsonDeserialize(using = StringOrListDeserializer.class)
    private List<String> code;

    private int pageNum = 1;

    private int pageSize = 10;

    public static class StringOrListDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<String> result = new ArrayList<>();
            if (node.isArray()) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    result.add(it.next().asText());
                }
            } else if (node.isTextual()) {
                String s = node.asText();
                if (s != null && !s.isEmpty()) {
                    for (String part : s.split(",")) {
                        result.add(part);
                    }
                }
            }
            return result;
        }
    }
}
