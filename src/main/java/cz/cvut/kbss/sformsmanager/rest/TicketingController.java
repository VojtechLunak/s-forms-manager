package cz.cvut.kbss.sformsmanager.rest;

import cz.cvut.kbss.sformsmanager.exception.ResourceNotFoundException;
import cz.cvut.kbss.sformsmanager.model.dto.FormTicketsInCategoriesDTO;
import cz.cvut.kbss.sformsmanager.model.dto.TicketDTO;
import cz.cvut.kbss.sformsmanager.model.persisted.local.QuestionTemplateSnapshot;
import cz.cvut.kbss.sformsmanager.model.persisted.local.RecordSnapshot;
import cz.cvut.kbss.sformsmanager.model.request.CreateTicketRequest;
import cz.cvut.kbss.sformsmanager.service.model.local.QuestionTemplateService;
import cz.cvut.kbss.sformsmanager.service.model.local.RecordService;
import cz.cvut.kbss.sformsmanager.service.model.local.TicketToProjectRelationsService;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketToProjectRelations;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/ticket")
public class TicketingController {


    private final TicketingService ticketingService;
    private final RecordService recordService;
    private final TicketToProjectRelationsService ticketToProjectRelationsService;
    private final QuestionTemplateService questionTemplateService;

    @Autowired
    public TicketingController(TicketingService ticketingService, RecordService recordService, TicketToProjectRelationsService ticketToProjectRelationsService, QuestionTemplateService questionTemplateService) {
        this.ticketingService = ticketingService;
        this.recordService = recordService;
        this.ticketToProjectRelationsService = ticketToProjectRelationsService;
        this.questionTemplateService = questionTemplateService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/project")
    public List<TicketDTO> getProjectTickets(@RequestParam(value = "projectName") String projectName) {
        return getProjectTicketsStream(projectName).collect(Collectors.toList());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/category")
    public FormTicketsInCategoriesDTO getTicketsInCategories(@RequestParam(value = "projectName") String projectName,
                                                             @RequestParam(value = "contextUri") String contextUri) {

        RecordSnapshot recordSnapshot = recordService.findRecordSnapshotByContextUri(projectName, URI.create(contextUri))
                .orElseThrow(() -> new ResourceNotFoundException("Record with context uri " + contextUri + " not found"));

        List<TicketDTO> projectTickets = getProjectTicketsStream(projectName).collect(Collectors.toList());

        List<TicketDTO> formTickets = ticketToProjectRelationsService.filterRecordSnapshotTickets(projectTickets, recordSnapshot);
        List<TicketDTO> formVersionTickets = ticketToProjectRelationsService.filterFormTemplateVersionTickets(projectTickets, recordSnapshot);
        List<TicketDTO> questionTickets = ticketToProjectRelationsService.filterQuestionTemplateTickets(projectTickets, recordSnapshot, projectName);

        return new FormTicketsInCategoriesDTO(formTickets, formVersionTickets, questionTickets);
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String create(@RequestBody CreateTicketRequest createTicketRequest) {
        TicketToProjectRelations relations = ticketToProjectRelationsService.createRelationsFromRequest(createTicketRequest);
        TicketDTO ticket = new TicketDTO(createTicketRequest.getName(), createTicketRequest.getDescription(), null, relations);

        // return URL of created ticket
        return ticketingService.createTicket(createTicketRequest.getProjectName(), ticket);
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String onRecordUpdate(@RequestParam String record) {
        CreateTicketRequest createTicketRequest = new CreateTicketRequest();
        createTicketRequest.setName("Record updated - " + record.split("/")[record.split("/").length - 1]);
        createTicketRequest.setDescription(record);

        TicketDTO ticket = new TicketDTO(createTicketRequest.getName(), createTicketRequest.getDescription(), null, null);
        return ticketingService.createTicket("record-updates from rm", ticket);
    }

    private Stream<TicketDTO> getProjectTicketsStream(String projectName) {
        return ticketingService.findProjectTickets(projectName).stream()
                .map(ticket -> {
                    String qtsLabel = ticketToProjectRelationsService.getTicketQuestionTemplateSnapshot(projectName, ticket.getTicketCustomRelations())
                            .map(QuestionTemplateSnapshot::getLabel)
                            .orElse(null);
                    TicketDTO.TicketToRelationsDTO relationsDTO = TicketDTO.TicketToRelationsDTO.createFormTicketRelations(ticket.getTicketCustomRelations(), qtsLabel);
                    return new TicketDTO(ticket.getName(), ticket.getDescription(), ticket.getUrl(), relationsDTO);
                });
    }
}
