package cz.cvut.kbss.sformsmanager.service.formgen;

import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.Obj;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.*;


@Service
public class FormTemplateExtractionService {

    public FormTemplateExtractionService() {
    }

    public String extractFormTemplateFromFormData(String jsonLdInput, String graphUri) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> fullJsonld = mapper.readValue(jsonLdInput, new TypeReference<>() {});
        List<Map<String, Object>> graph = (List<Map<String, Object>>) fullJsonld.get("@graph");

        if (graph == null) {
            throw new IllegalArgumentException("Missing @graph in JSON-LD structure");
        }

        // Step 1: Remove answer nodes
        List<Map<String, Object>> filteredGraph = new ArrayList<>();
        for (Map<String, Object> node : graph) {
            List<String> types = getAsList(node.get("@type"));
            if (!types.contains("doc:answer")) {
                // Remove has_answer fields
                node.remove("has_answer");
                node.remove("http://onto.fel.cvut.cz/ontologies/form/has_answer");
                filteredGraph.add(node);
            }
        }

        // Step 2: Build @id → has-question-origin map
        Map<String, Object> idToOriginMap = new HashMap<>();
        for (Map<String, Object> node : filteredGraph) {
            Object id = node.get("@id");
            Object origin = node.get("has-question-origin");
            if (id != null && origin != null) {
                idToOriginMap.put(id.toString(), origin);
            }
        }

        // Step 3: Recursively update all references to use origin
        List<Map<String, Object>> updatedGraph = new ArrayList<>();
        for (Map<String, Object> node : filteredGraph) {
            Object updatedNodeObj = updateIdsRecursively(node, idToOriginMap);
            if (updatedNodeObj instanceof Map<?, ?>) {
                Map<String, Object> updatedNode = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) updatedNodeObj).entrySet()) {
                    updatedNode.put(entry.getKey().toString(), entry.getValue());
                }
                if (updatedNode.get("has-question-origin") != null) {
                    updatedNode.put("@id", updatedNode.get("has-question-origin"));
                }
                updatedGraph.add(updatedNode);
            } else {
                throw new IllegalArgumentException("Unexpected structure returned by updateIdsRecursively");
            }
        }

        // Step 4: Return compacted structure
        Map<String, Object> result = new HashMap<>();
        result.put("@context", fullJsonld.get("@context"));
        result.put("@graph", updatedGraph);
        return mapper.writeValueAsString(flattenJsonLdStructure(result, graphUri));
    }

    private Object updateIdsRecursively(Object node, Map<String, Object> idToOriginMap) {
        if (node instanceof List<?>) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) node) {
                if (item instanceof String && idToOriginMap.containsKey(item)) {
                    result.add(idToOriginMap.get(item));
                } else {
                    result.add(updateIdsRecursively(item, idToOriginMap));
                }
            }
            return result;
        } else if (node instanceof Map<?, ?>) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String && idToOriginMap.containsKey(value)) {
                    result.put(entry.getKey().toString(), idToOriginMap.get(value));
                } else {
                    result.put(entry.getKey().toString(), updateIdsRecursively(value, idToOriginMap));
                }
            }
            return result;
        }
        return node;
    }

    private List<String> getAsList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        } else if (obj instanceof String) {
            return Collections.singletonList((String) obj);
        }
        return Collections.emptyList();
    }

    private Map<String, Object> flattenJsonLdStructure(Map<String, Object> inputJsonLd, String graphUri) {
        // Expand the JSON-LD structure using the @context
        Object expanded = JsonLdProcessor.expand(inputJsonLd);

        // Convert the expanded structure to a List of Maps
        List<Map<String, Object>> expandedList = (List<Map<String, Object>>) expanded;

        // Find the node with the full URI of "form-template"
        Map<String, Object> formTemplateNode = expandedList.stream()
                .filter(node -> {
                    Object type = node.get("@type");
                    if (type instanceof List<?>) {
                        return ((List<?>) type).contains("http://onto.fel.cvut.cz/ontologies/form/form-template");
                    } else if (type instanceof String) {
                        return "http://onto.fel.cvut.cz/ontologies/form/form-template".equals(type);
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No node with @type 'form-template' found"));
        formTemplateNode.put("@id", graphUri);
        formTemplateNode.put("http://onto.fel.cvut.cz/ontologies/form/has-question-origin", graphUri);

        // Build the result
        Map<String, Object> result = Map.of(
                "@id", formTemplateNode.get("@id"),
                "@graph", expandedList
        );

        return result;
    }
}
