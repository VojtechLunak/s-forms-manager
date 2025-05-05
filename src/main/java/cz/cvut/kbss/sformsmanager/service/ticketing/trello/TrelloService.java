package cz.cvut.kbss.sformsmanager.service.ticketing.trello;

import com.julienvey.trello.domain.Card;
import com.julienvey.trello.domain.Label;
import com.julienvey.trello.domain.Member;
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
        card.setDesc(ticket.getDescription());

        // assign the member to the card if email is provided
        if (ticket.getMemberEmail() != null && !ticket.getMemberEmail().isEmpty()) {
            Member member = getTrelloMemberByEmail(ticket.getMemberEmail());
            card.setIdMembers(List.of(member.getId()));
        }

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

    @Override
    public void moveTicketToDeployed(String cardId) {
        List<TList> boardLists = trelloClient.getBoardLists(boardId);
        if (boardLists.isEmpty()) {
            throw new TrelloException("Trello board does not have any lists!");
        }

        TList deployedList = boardLists.stream()
                .filter(list -> list.getName().equals("DEPLOYED"))
                .findAny()
                .orElseThrow(() -> new TrelloException("Trello list 'DEPLOYED' not found."));

        Card card = this.getCardByShortId(cardId);
        if (card == null) {
            throw new TrelloException("Trello card with ID: " + cardId + " not found.");
        } else {
            trelloClient.moveCardToList(card.getId(), deployedList.getId());
        }
    }

    @Override
    public void moveAllTicketsToDeployed() {
        List<TList> boardLists = trelloClient.getBoardLists(boardId);
        if (boardLists.isEmpty()) {
            throw new TrelloException("Trello board does not have any lists!");
        }

        List<Card> boardCards = trelloClient.getBoardCards(boardId);
        if (boardCards.isEmpty()) {
            throw new TrelloException("Trello board does not have any cards!");
        }

        List<String> listIds = boardLists.stream()
                .filter(list -> list.getName().equals("OPEN") || list.getName().equals("TODO") || list.getName().equals("IN PROGRESS"))
                .map(TList::getId)
                .collect(Collectors.toList());

        TList deployedList = boardLists.stream()
                .filter(list -> list.getName().equals("DEPLOYED"))
                .findAny()
                .orElseThrow(() -> new TrelloException("Trello list 'DEPLOYED' not found."));

        boardCards.forEach(boardCard -> {
            if (!listIds.contains(boardCard.getIdList())) {
                trelloClient.moveCardToList(boardCard.getId(), deployedList.getId());
            }
        });
    }

    private Member getTrelloMemberByEmail(String email) {
        List<Member> members = trelloClient.getBoardMembers(boardId);
        if (members.isEmpty()) {
            throw new TrelloException("Trello board does not have any members!");
        }

        return members.stream()
                .map(m -> trelloClient.getMemberInformation(m.getUsername()))
                .filter(m -> {
                    // logic to work with the fact that Trello API might not return the email - for email test.domain456@email.com, username testdomain456 is valid
                    if (m.getEmail() == null) {
                        return m.getUsername().contains(email.substring(0, email.indexOf("@")).replaceAll("[^a-zA-Z0-9]",""));
                    } else {
                        return m.getEmail().equals(email);
                    }

                })
                .findAny()
                .orElseThrow(() -> new TrelloException("Trello member with email: " + email + " not found."));
    }

    private Label getProjectLabel(String projectName) {
        List<Label> existingLabels = trelloClient.getBoardLabels(boardId);
        return existingLabels.stream()
                .filter(label -> label.getName().equals(projectName))
                .findAny()
                .orElseThrow(() -> new TrelloException("There is no label with name: " + projectName));
    }

    private Card getCardByShortId(String shortId) {
        List<Card> cards = trelloClient.getBoardCards(boardId);
        return cards.stream()
                .filter(card -> card.getIdShort().equals(shortId))
                .findAny()
                .orElseThrow(() -> new TrelloException("Card with short ID: " + shortId + " not found."));
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
