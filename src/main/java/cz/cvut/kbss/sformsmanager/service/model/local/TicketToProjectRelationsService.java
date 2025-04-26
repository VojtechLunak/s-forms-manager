package cz.cvut.kbss.sformsmanager.service.model.local;

import cz.cvut.kbss.sformsmanager.exception.RecordSnapshotNotFound;
import cz.cvut.kbss.sformsmanager.exception.VersionNotFoundException;
import cz.cvut.kbss.sformsmanager.model.dto.TicketDTO;
import cz.cvut.kbss.sformsmanager.model.persisted.local.FormTemplateVersion;
import cz.cvut.kbss.sformsmanager.model.persisted.local.QuestionTemplateSnapshot;
import cz.cvut.kbss.sformsmanager.model.persisted.local.RecordSnapshot;
import cz.cvut.kbss.sformsmanager.model.request.CreateTicketRequest;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketToProjectRelations;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TicketToProjectRelationsService {
    private final TicketingService ticketingService;
    private final RecordService recordService;
    private final FormTemplateService formTemplateService;
    private final QuestionTemplateService questionTemplateService;

    public TicketToProjectRelationsService(TicketingService ticketingService, RecordService recordService, FormTemplateService formTemplateService, QuestionTemplateService questionTemplateService) {
        this.ticketingService = ticketingService;
        this.recordService = recordService;
        this.formTemplateService = formTemplateService;
        this.questionTemplateService = questionTemplateService;
    }

    @Transactional
    public TicketToProjectRelations createRelationsFromRequest(CreateTicketRequest createTicketRequest) {
        String projectName = createTicketRequest.getProjectName();

        RecordSnapshot recordSnapshot = recordService.findByRemoteContextUri(projectName, createTicketRequest.getRecordContextUri())
               .orElseThrow(() -> new RecordSnapshotNotFound("RecordSnapshot specified at the ticket not found. Key: " + createTicketRequest.getRecordContextUri()));


        String recordSnapshotRelationContextUri = null;
        if (createTicketRequest.isRelateToRecordSnapshot()) {
            recordSnapshotRelationContextUri = createTicketRequest.getRecordContextUri();
        }

        String formVersionRelationId = null;
        if (createTicketRequest.isRelateToFormVersion()) {
            FormTemplateVersion formTemplateVersion = recordSnapshot.getFormTemplateVersion();
            formVersionRelationId = formTemplateVersion.getKey();
        }

        String questionRelationQOP = null;
        if (createTicketRequest.isRelateToQuestion()) {
            questionRelationQOP = createTicketRequest.getQuestionOriginPath();
        }

        return ticketingService.createRelations(recordSnapshotRelationContextUri, formVersionRelationId, questionRelationQOP);
    }

    // extracting the ticket relations to records based on the description - custom field in trello are premium feature as of 04/2025
    public List<TicketDTO> filterRecordSnapshotTicketsFromDescription(List<TicketDTO> projectTickets, RecordSnapshot recordSnapshot) {
        return projectTickets.stream().filter(ticket -> {
            String ticketDescription = ticket.getDescription();
            return ticketDescription != null && ticketDescription.contains(recordSnapshot.getKey());
        }).collect(Collectors.toList());
    }

    public List<TicketDTO> filterRecordSnapshotTickets(List<TicketDTO> projectTickets, RecordSnapshot recordSnapshot) {
        return projectTickets.stream().filter(ticket -> {
            String formRelationId = ticket.getProjectRelations().getRelatedRecordSnapshot();
            return formRelationId != null && formRelationId.equals(recordSnapshot.getRemoteContextURI().toString());
        }).collect(Collectors.toList());
    }

    public List<TicketDTO> filterFormTemplateVersionTickets(List<TicketDTO> projectTickets, RecordSnapshot recordSnapshot) {
        return projectTickets.stream().filter(ticket -> {
            String formVersionRelationId = ticket.getProjectRelations().getRelatedFormVersion();

            // check if the ticket version relation id is either the traditional FormTemplateVersion id or its internal name
            return formVersionRelationId != null && formVersionRelationId.equals(recordSnapshot.getFormTemplateVersion().getKey());
        }).collect(Collectors.toList());
    }

    @Transactional
    public List<TicketDTO> filterQuestionTemplateTickets(List<TicketDTO> projectTickets, RecordSnapshot recordSnapshot, String projectName) {
        Map<String, String> questionTemplates = questionTemplateService.findAllFormTemplateVersionQuestionSnapshots(projectName, recordSnapshot.getFormTemplateVersion().getUri())
                .stream().collect(Collectors.toMap(q -> q.getQuestionOriginPath(), q -> q.getLabel(), (a, b) -> a));

        // check if the question origin path corresponds to the record snapshot form version
        return projectTickets.stream().filter(ticket -> {
            String ticketQOP = ticket.getProjectRelations().getRelatedQuestionOriginPath();
            return ticketQOP != null && questionTemplates.containsKey(ticketQOP);
        }).collect(Collectors.toList());
    }

    @Transactional
    public Optional<QuestionTemplateSnapshot> getTicketQuestionTemplateSnapshot(String projectName, TicketToProjectRelations ticketToProjectRelations) {
        if (ticketToProjectRelations.getRelatedQuestionOriginPath() == null) {
            return Optional.empty();
        }
        List<QuestionTemplateSnapshot> questionTemplateSnapshots = questionTemplateService.findByQOP(projectName, ticketToProjectRelations.getRelatedQuestionOriginPath());
        if (questionTemplateSnapshots.isEmpty()) {
            return Optional.empty();
        }

        Optional<FormTemplateVersion> formTemplateVersionOpt = Optional.empty();

        // if there's a form template version id specified, use it directly
        if (ticketToProjectRelations.getRelatedFormVersion() != null) {
            formTemplateVersionOpt = formTemplateService.findVersionByKey(projectName, ticketToProjectRelations.getRelatedFormVersion());
        }

        // if there's just the record snapshot identifier, use its form template version instead
        if (!formTemplateVersionOpt.isPresent() && ticketToProjectRelations.getRelatedRecordSnapshot() != null) {
            formTemplateVersionOpt = recordService.getFormTemplateVersion(projectName, ticketToProjectRelations.getRelatedRecordSnapshot());
        }

        // if FormTemplateVersion is not recognized at this point, take any from the project or throw exception
        FormTemplateVersion formTemplateVersion = formTemplateVersionOpt.orElseGet(() ->
                formTemplateService.findAllVersions(projectName).stream()
                        .findAny()
                        .orElseThrow(() -> new VersionNotFoundException("No FormTemplateVersion is found at the project.")));

        return Optional.of(questionTemplateSnapshots.stream()
                .filter(qts -> qts.getFormTemplateVersion().equals(formTemplateVersion))
                .findAny()
                .orElseGet(() -> questionTemplateSnapshots.get(0)));
    }

}
