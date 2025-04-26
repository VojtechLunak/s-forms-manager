package cz.cvut.kbss.sformsmanager.service.ticketing.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.jsonldjava.shaded.com.google.common.collect.Lists;
import com.julienvey.trello.TrelloHttpClient;
import com.julienvey.trello.domain.Argument;
import com.julienvey.trello.domain.TrelloEntity;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.TrelloUrl;
import com.julienvey.trello.impl.http.ApacheHttpClient;
import cz.cvut.kbss.sformsmanager.exception.TrelloException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TrelloClientWithCustomFields extends TrelloImpl {

    private final String applicationKey;
    private final String accessToken;
    private final TrelloHttpClient client;

    public TrelloClientWithCustomFields(String applicationKey, String accessToken) {
        this(applicationKey, accessToken, new ApacheHttpClient());
    }

    public TrelloClientWithCustomFields(String applicationKey, String accessToken, TrelloHttpClient httpClient) {
        super(applicationKey, accessToken, httpClient);
        this.client = httpClient;
        this.applicationKey = applicationKey;
        this.accessToken = accessToken;
    }

    public void moveCardToList(String cardId, String listId) throws TrelloException {
        String url = "https://api.trello.com/1/cards/" + cardId + "?idList=" + listId + "&key=" + applicationKey + "&token=" + accessToken;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new TrelloException("Failed to move card with id " + cardId + " to list with id " + listId);
        }
    }

    public void updateCustomFieldOnCard(String cardId, String customFieldId, CustomFieldValueWrapper value, Argument... args) {
        String url = TrelloUrl.createUrl("/cards/{cardId}/customField/{customFieldId}/item?").params(args).asString();
        this.client.putForObject(url, value, CustomFieldValue.class, cardId, customFieldId, applicationKey, accessToken);
    }

    public List<CustomField> getCardCustomFields(String cardId, Argument... args) {
        String url = TrelloUrl.createUrl("/cards/{cardId}/customFieldItems?").params(args).asString();
        return Lists.newArrayList(this.client.get(url, CustomField[].class, cardId, applicationKey, accessToken));
    }

    /**
     * Get Map of ID as a key and CustomFieldDefinition as a value.
     *
     * @param boardId
     * @param args
     * @return map of id and definitions
     */
    public Map<String, CustomFieldDefinition> getCustomFieldDefinitions(String boardId, Argument... args) {
        String url = TrelloUrl.createUrl("/boards/{boardId}/customFields?").params(args).asString();
        return Arrays.stream(this.client.get(url, CustomFieldDefinition[].class, boardId, applicationKey, accessToken)).collect(Collectors.toMap(cfd -> cfd.getId(), Function.identity()));
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    static class CustomField extends TrelloEntity {
        private String id;
        private CustomFieldValue value;
        private String idCustomField;

        public CustomField() {
        }

        public CustomField(String id, CustomFieldValue value, String idModel) {
            this.id = id;
            this.value = value;
            this.idCustomField = idModel;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public CustomFieldValue getValue() {
            return value;
        }

        public void setValue(CustomFieldValue value) {
            this.value = value;
        }

        public String getIdCustomField() {
            return idCustomField;
        }

        public void setIdCustomField(String idCustomField) {
            this.idCustomField = idCustomField;
        }
    }


    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    static class CustomFieldValue extends TrelloEntity {
        private String text;

        public CustomFieldValue() {
        }

        public CustomFieldValue(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    @JsonIgnoreProperties(
            ignoreUnknown = true
    )
    static class CustomFieldDefinition extends TrelloEntity {
        private String id;
        private String name;

        public CustomFieldDefinition() {
        }

        public CustomFieldDefinition(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class CustomFieldValueWrapper {
        private CustomFieldValue value;

        public CustomFieldValueWrapper() {
        }

        public CustomFieldValueWrapper(String value) {
            this.value = new CustomFieldValue(value);
        }

        public CustomFieldValue getValue() {
            return value;
        }

        public void setValue(CustomFieldValue value) {
            this.value = value;
        }
    }
}
