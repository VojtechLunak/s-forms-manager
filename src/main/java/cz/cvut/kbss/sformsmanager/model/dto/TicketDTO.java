package cz.cvut.kbss.sformsmanager.model.dto;

import cz.cvut.kbss.sformsmanager.service.ticketing.Ticket;
import cz.cvut.kbss.sformsmanager.service.ticketing.TicketToProjectRelations;

public class TicketDTO implements Ticket {

    private final String name;

    private final String description;

    private final String url;

    private String memberEmail;

    private final TicketToProjectRelations projectRelations;

    public TicketDTO(String name, String description, String url, TicketToProjectRelations projectRelations) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.projectRelations = projectRelations;
    }

    public TicketDTO(String name, String description, String url, TicketToProjectRelations projectRelations, String memberEmail) {
        this(name, description, url, projectRelations);
        this.memberEmail = memberEmail;
    }

    public String getMemberEmail() {
        return memberEmail;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TicketToProjectRelations getProjectRelations() {
        return projectRelations;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public TicketToProjectRelations getTicketCustomRelations() {
        return projectRelations;
    }

    public static class TicketToRelationsDTO implements TicketToProjectRelations {
        private final String relatedForm;
        private final String relatedFormVersion;
        private final String relatedQuestionOriginPath;
        private final String relatedQuestionLabel;

        public static TicketToRelationsDTO createFormTicketRelations(TicketToProjectRelations ticketRelations, String relatedQuestionLabel) {
            return new TicketToRelationsDTO(ticketRelations.getRelatedRecordSnapshot(),
                    ticketRelations.getRelatedFormVersion(),
                    ticketRelations.getRelatedQuestionOriginPath(),
                    relatedQuestionLabel);
        }

        public TicketToRelationsDTO(String relatedForm, String relatedFormVersion, String relatedQuestionOriginPath, String relatedQuestionLabel) {
            this.relatedForm = relatedForm;
            this.relatedFormVersion = relatedFormVersion;
            this.relatedQuestionOriginPath = relatedQuestionOriginPath;
            this.relatedQuestionLabel = relatedQuestionLabel;
        }

        @Override
        public String getRelatedRecordSnapshot() {
            return relatedForm;
        }

        public String getRelatedFormVersion() {
            return relatedFormVersion;
        }

        public String getRelatedQuestionOriginPath() {
            return relatedQuestionOriginPath;
        }

        public String getRelatedQuestionLabel() {
            return relatedQuestionLabel;
        }

    }
}
