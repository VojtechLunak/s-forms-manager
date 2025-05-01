package cz.cvut.kbss.sformsmanager.service.ticketing;

public interface Ticket {

    String getName();

    String getDescription();

    String getUrl();

    String getMemberEmail();

    TicketToProjectRelations getTicketCustomRelations();

}
