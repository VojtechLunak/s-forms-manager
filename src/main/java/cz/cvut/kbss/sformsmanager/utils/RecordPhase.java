package cz.cvut.kbss.sformsmanager.utils;

public enum RecordPhase {
    COMPLETE("http://onto.fel.cvut.cz/ontologies/record-manager/complete-record-phase"),
    OPEN("http://onto.fel.cvut.cz/ontologies/record-manager/open-record-phase"),
    REJECTED("http://onto.fel.cvut.cz/ontologies/record-manager/reject-record-phase");

    private final String value;

    RecordPhase(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
