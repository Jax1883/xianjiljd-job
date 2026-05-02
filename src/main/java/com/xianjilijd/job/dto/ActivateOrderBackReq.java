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
public class ActivateOrderBackReq {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @JsonDeserialize(using = LongStringOrListDeserializer.class)
    private List<Long> requestIds;

    public static class LongStringOrListDeserializer extends JsonDeserializer<List<Long>> {
        @Override
        public List<Long> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<Long> result = new ArrayList<>();
            if (node.isArray()) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    JsonNode el = it.next();
                    if (el.isNumber()) {
                        result.add(el.asLong());
                    } else {
                        String s = el.asText();
                        if (s != null && !s.trim().isEmpty()) {
                            result.add(Long.parseLong(s.trim()));
                        }
                    }
                }
            } else if (node.isTextual()) {
                String s = node.asText();
                if (s != null && !s.isEmpty()) {
                    for (String part : s.split(",")) {
                        if (!part.trim().isEmpty()) {
                            result.add(Long.parseLong(part.trim()));
                        }
                    }
                }
            } else if (node.isNumber()) {
                result.add(node.asLong());
            }
            return result;
        }
    }
}
