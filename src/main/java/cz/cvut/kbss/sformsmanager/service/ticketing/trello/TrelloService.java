package cz.cvut.kbss.sformsmanager.service.ticketing.trello;

import com.julienvey.trello.domain.Card;
import com.julienvey.trello.domain.Label;
import com.julienvey.trello.domain.TList;
import cz.cvut.kbss.sformsmanager.exception.TrelloException;
import cz.cvut.kbss.sformsmanager.service.ticketing.Ticket;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketToProjectRelations;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrelloService implements TicketingService {

    @Value("${trello.board-id}")
    private String boardId;

    @Value("${trello.default-list-id}")
    private String defaultListId;

    @Value("${trello.default-list-name}")
    private String defaultListName;

    private final TrelloClientWithCustomFields trelloClient;

    @Autowired
    public TrelloService(TrelloClientWithCustomFields trelloClient) {
        this.trelloClient = trelloClient;
    }

    @Override
    public List<TrelloTicket> findProjectTickets(String projectName) {
        return trelloClient.getBoardCards(boardId).stream()
                .filter(card -> card.getLabels().stream().anyMatch(label -> label.getName().equals(projectName)))
                .map(card -> {
                    Map<String, String> customFields = findTicketCustomFields(card.getId());
                    TrelloCustomFields fields = new TrelloCustomFields(customFields);

                    return new TrelloTicket(card.getName(), card.getDesc(), card.getUrl(), fields);
                }).collect(Collectors.toList());
    }

    @Override
    public Map<String, String> findTicketCustomFields(String ticketId) {
        Map<String, TrelloClientWithCustomFields.CustomFieldDefinition> customFieldDefinitions = trelloClient.getCustomFieldDefinitions(boardId);
        return trelloClient.getCardCustomFields(ticketId).stream()
                .map(cf -> new TrelloCustomField(customFieldDefinitions.get(cf.getIdCustomField()).getName(), cf.getValue().getText()))
                .collect(Collectors.toMap(cf -> cf.getName(), cf -> cf.getValue()));
    }

    @Override
    public String createTicket(String projectName, Ticket ticket) {
        Card card = new Card();
        card.setName(ticket.getName());
        card.setDesc(ticket.getDescription()); // TODO: add link to the form

        // create ticket
        card = trelloClient.createCard(getNewCardListId(), card);

        // add project label
        // the Trello API does not work as expected here!
        // card.addLabels(getProjectLabel(projectName).getId());
        card.addLabels(projectName);

        // get the custom field definitions
        Map<String, TrelloClientWithCustomFields.CustomFieldDefinition> customFieldDefinitions = trelloClient.getCustomFieldDefinitions(boardId);
        TrelloCustomFields usedCustomFields = (TrelloCustomFields) ticket.getTicketCustomRelations();

        // update (add) the custom field values
        final Card finalCardRef = card;
        customFieldDefinitions.entrySet().stream().forEach(entry -> {
            if (!usedCustomFields.getMap().containsKey(entry.getValue().getName())) {
                return;
            }
            String customFieldId = entry.getKey();
            String customFieldValue = usedCustomFields.getMap().get(entry.getValue().getName()); // get by name

            TrelloClientWithCustomFields.CustomFieldValueWrapper value = new TrelloClientWithCustomFields.CustomFieldValueWrapper(customFieldValue);
            trelloClient.updateCustomFieldOnCard(finalCardRef.getId(), customFieldId, value);
        });
        return card.getUrl() != null ? card.getUrl() : null;
    }


    @Override
    public TicketToProjectRelations createRelations(String formRelationId, String formVersionRelationId, String questionRelationId) {
        return new TrelloCustomFields(formRelationId, formVersionRelationId, questionRelationId);
    }

    private Label getProjectLabel(String projectName) {
        List<Label> existingLabels = trelloClient.getBoardLabels(boardId);
        return existingLabels.stream()
                .filter(label -> label.getName().equals(projectName))
                .findAny()
                .orElseThrow(() -> new TrelloException("There is no label with name: " + projectName));
    }

    private String getNewCardListId() {
        List<TList> boardLists = trelloClient.getBoardLists(boardId);
        if (boardLists.isEmpty()) {
            throw new TrelloException("Trello board does not have any lists!");
        }

        if (defaultListName != null && !defaultListName.isEmpty()) {
            return boardLists.stream()
                    .filter(list -> list.getName().equals(defaultListName))
                    .map(list -> list.getId())
                    .findAny()
                    .orElseThrow(() -> new TrelloException("Trello default board not found by name: " + defaultListId + "."));
        } else if (defaultListId != null && !defaultListId.isEmpty()) {
            return boardLists.stream()
                    .filter(list -> list.getId().equals(defaultListId))
                    .map(list -> list.getId())
                    .findAny()
                    .orElseThrow(() -> new TrelloException("Trello default board not found by ID: " + defaultListId + "."));
        } else {
            return boardLists.get(0).getId();
        }

    }
}
