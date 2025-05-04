package cz.cvut.kbss.sformsmanager.rest;

import cz.cvut.kbss.sformsmanager.exception.ResourceNotFoundException;
import cz.cvut.kbss.sformsmanager.model.dto.FormTicketsInCategoriesDTO;
import cz.cvut.kbss.sformsmanager.model.dto.TicketDTO;
import cz.cvut.kbss.sformsmanager.model.persisted.local.Project;
import cz.cvut.kbss.sformsmanager.model.persisted.local.QuestionTemplateSnapshot;
import cz.cvut.kbss.sformsmanager.model.persisted.local.RecordSnapshot;
import cz.cvut.kbss.sformsmanager.model.request.CreateTicketRequest;
import cz.cvut.kbss.sformsmanager.service.formgen.RemoteFormGenJsonLoader;
import cz.cvut.kbss.sformsmanager.service.model.local.ProjectService;
import cz.cvut.kbss.sformsmanager.service.model.local.QuestionTemplateService;
import cz.cvut.kbss.sformsmanager.service.model.local.RecordService;
import cz.cvut.kbss.sformsmanager.service.model.local.TicketToProjectRelationsService;
import cz.cvut.kbss.sformsmanager.service.process.RemoteDataProcessingOrchestrator;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketToProjectRelations;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketingService;
import cz.cvut.kbss.sformsmanager.utils.RecordPhase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/ticket")
public class TicketingController {


    private final TicketingService ticketingService;
    private final RecordService recordService;
    private final TicketToProjectRelationsService ticketToProjectRelationsService;
    private final RemoteFormGenJsonLoader remoteFormGenJsonLoader;
    private final RemoteDataProcessingOrchestrator processingService;
    private final ProjectService projectService;
    private final QuestionTemplateService questionTemplateService;

    @Autowired
    public TicketingController(TicketingService ticketingService, RecordService recordService, TicketToProjectRelationsService ticketToProjectRelationsService, RemoteFormGenJsonLoader remoteFormGenJsonLoader, RemoteDataProcessingOrchestrator processingService, ProjectService projectService, QuestionTemplateService questionTemplateService) {
        this.ticketingService = ticketingService;
        this.recordService = recordService;
        this.ticketToProjectRelationsService = ticketToProjectRelationsService;
        this.remoteFormGenJsonLoader = remoteFormGenJsonLoader;
        this.processingService = processingService;
        this.projectService = projectService;
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

        List<TicketDTO> formTickets = ticketToProjectRelationsService.filterRecordSnapshotTicketsFromDescription(projectTickets, recordSnapshot);
        List<TicketDTO> formVersionTickets = ticketToProjectRelationsService.filterFormTemplateVersionTickets(projectTickets, recordSnapshot);
        List<TicketDTO> questionTickets = ticketToProjectRelationsService.filterQuestionTemplateTickets(projectTickets, recordSnapshot, projectName);

        return new FormTicketsInCategoriesDTO(formTickets, formVersionTickets, questionTickets);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/resolve")
    public ResponseEntity<String> resolveIssueAndOpenRecord(@RequestParam(value = "ticketId") String ticketId, @RequestParam(value = "formGenUri") String formGenUri, @RequestParam(value = "projectName") String projectName) {
        Project project = projectService.findByKey(projectName).orElseThrow();
        //remoteFormGenJsonLoader.changeRecordPhaseForFormGenSPARQL(formGenUri, RecordPhase.OPEN, project.getFormGenRepositoryUrl(), project.getAppRepositoryUrl());
        remoteFormGenJsonLoader.changeRecordPhaseForFormGen(formGenUri, RecordPhase.OPEN, project.getFormGenRepositoryUrl());
        try {
            ticketingService.moveTicketToDeployed(ticketId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.GET, path = "/resolveAll")
    public ResponseEntity<String> resolveAllIssuesAndOpenAllRecords(@RequestParam(value = "projectName") String projectName) {
        Project project = projectService.findByKey(projectName).orElseThrow();
        //remoteFormGenJsonLoader.changeRecordPhaseForAllRecordsSPARQL(RecordPhase.OPEN, project.getAppRepositoryUrl());
        remoteFormGenJsonLoader.changeRecordPhaseForAllRecords(RecordPhase.OPEN);
        try {
            ticketingService.moveAllTicketsToDeployed();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String create(@RequestBody CreateTicketRequest createTicketRequest) {
        TicketToProjectRelations relations = ticketToProjectRelationsService.createRelationsFromRequest(createTicketRequest);
        TicketDTO ticket = new TicketDTO(createTicketRequest.getName(), createTicketRequest.getDescription(), null, relations);

        // return URL of created ticket
        return ticketingService.createTicket(createTicketRequest.getProjectName(), ticket);
    }

    /***
     * This method is called when a record is updated in the remote Record Manager. There is no
     * mechanism to ensure from where the update is called, so it is assumed that the update is from
     * the "default" project in SForms Manager.
     * @param record URI of Record in remote Record Manager that is being updated
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String onRecordUpdate(@RequestParam String record) throws URISyntaxException, IOException {
        CreateTicketRequest createTicketRequest = new CreateTicketRequest();
        String recordKey = record.split("/")[record.split("/").length - 1];

        String timestamp = String.valueOf(System.currentTimeMillis());
        String formGenURI = "http://onto.fel.cvut.cz/ontologies/record-manager/formGenVirtual" + timestamp;

        // get the default project
        Project project = projectService.findAll().get(0);

        // create virtual formGen from updated record in case of REJECT or COMPLETE
        remoteFormGenJsonLoader.generateVirtualFormGen(record, formGenURI, project.getAppRepositoryUrl());

        // export the virtual formGen record
        String exportedVirtualFormGen = remoteFormGenJsonLoader.exportGraph(formGenURI, project.getAppRepositoryUrl());

        // import the virtual formGen record into the formGen repository
        remoteFormGenJsonLoader.importGraph(formGenURI, project.getFormGenRepositoryUrl(), exportedVirtualFormGen);

        // delete the virtual formGen record from the app repository
        remoteFormGenJsonLoader.deleteGraph(formGenURI, project.getAppRepositoryUrl());

        Map<String, String> metadata = remoteFormGenJsonLoader.getRecordMetadata(formGenURI, record, project.getFormGenRepositoryUrl());
        String phase = metadata.get("Phase");
        String label = metadata.get("Label");
        String authorEmail = metadata.get("Email");
        createTicketRequest.setName("Record issue: " + label);
        // we do not need phase in description
        metadata.remove("Phase");
        metadata.remove("Label");
        metadata.remove("email");
        if (phase == null || !phase.equals(RecordPhase.REJECTED.toString())) {
            return "No ticket created. Record phase is not 'rejected'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Record metadata:\n");
        metadata.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value).append("\n"));

        //Add link to the ticket description - SForms Editor [generalize the url?]
        String sfeLink = "https://tomasklima.vercel.app/?formUrl=http://localhost:8080/rest/sforms/s-forms-json-ld/" + project.getKey() + "/" + formGenURI.substring(formGenURI.lastIndexOf('/') + 1);
        sb.append("\nLink to the SForms Editor: ").append(sfeLink).append("\n");

        //Add link to the ticket description - Record Manager
        String rmLink = "http://localhost:1235/record-manager/records/" + recordKey;
        sb.append("\nLink to the Record Manager: ").append(rmLink).append("\n");

        createTicketRequest.setRecordContextUri(formGenURI);
        createTicketRequest.setRelateToRecordSnapshot(true);
        createTicketRequest.setRelateToFormVersion(true);

        // process new formGen as Record Snapshotd
        String projectName = project.getKey();
        processingService.processDataSnapshotInRemoteContext(projectName, URI.create(formGenURI));
        RecordSnapshot recordSnapshot = recordService.findByRemoteContextUri(projectName, formGenURI)
                .orElseThrow(() -> new ResourceNotFoundException("Record Snapshot with context uri " + formGenURI + " not found"));

        String linkUrl = "http://localhost:3000/browse/forms/" + projectName + "/record/" + recordSnapshot.getKey();
        sb.append("\nLink to the SForms Manager: ").append(linkUrl).append("\n");
        createTicketRequest.setDescription(sb.toString());
        createTicketRequest.setProjectName(projectName);

        // create ticket relations
        TicketToProjectRelations relations = ticketToProjectRelationsService.createRelationsFromRequest(createTicketRequest);

        TicketDTO ticket = new TicketDTO(createTicketRequest.getName(), createTicketRequest.getDescription(), null, relations, authorEmail);
        return ticketingService.createTicket(createTicketRequest.getProjectName(), ticket);
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
