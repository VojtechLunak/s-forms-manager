package cz.cvut.kbss.sformsmanager.model.persisted.local;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.sformsmanager.model.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.Set;

@OWLClass(iri = Vocabulary.RecordSnapshot)
public class RecordSnapshot extends LocalEntity implements Serializable {

    @OWLObjectProperty(iri = Vocabulary.p_hasRecord, fetch = FetchType.EAGER)
    private Record record;

    @OWLObjectProperty(iri = Vocabulary.p_hasRecordVersion, fetch = FetchType.EAGER)
    private RecordVersion recordVersion;

    @OWLObjectProperty(iri = Vocabulary.p_hasFormTemplateVersion, fetch = FetchType.EAGER)
    private FormTemplateVersion formTemplateVersion;

    @OWLObjectProperty(iri = Vocabulary.p_hasSubmittedAnswers, fetch = FetchType.EAGER)
    private Set<SubmittedAnswer> answers;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.p_recordSnapshotCreated)
    private Date recordSnapshotCreated;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.p_hasRemoteContextURI)
    private URI remoteContextURI;

    @OWLDataProperty(iri = Vocabulary.p_hasNumberOfAnswers)
    private Integer numberOfAnswers;

    // key is the RECORD-URI -> hash

    public RecordSnapshot() {
    }

    public RecordSnapshot(String recordUriHashKey,
                          Record record,
                          RecordVersion recordVersion,
                          FormTemplateVersion formTemplateVersion,
                          Set<SubmittedAnswer> answers,
                          Date recordSnapshotCreated,
                          URI remoteContextURI,
                          Integer numberOfAnswers) {
        super(recordUriHashKey);
        this.record = record;
        this.recordVersion = recordVersion;
        this.formTemplateVersion = formTemplateVersion;
        this.answers = answers;
        this.recordSnapshotCreated = recordSnapshotCreated;
        this.remoteContextURI = remoteContextURI;
        this.numberOfAnswers = numberOfAnswers;
    }

    public RecordVersion getRecordVersion() {
        return recordVersion;
    }

    public void setRecordVersion(RecordVersion recordVersion) {
        this.recordVersion = recordVersion;
    }

    public FormTemplateVersion getFormTemplateVersion() {
        return formTemplateVersion;
    }

    public void setFormTemplateVersion(FormTemplateVersion formTemplateVersion) {
        this.formTemplateVersion = formTemplateVersion;
    }

    public Date getRecordSnapshotCreated() {
        return recordSnapshotCreated;
    }

    public void setRecordSnapshotCreated(Date recordSnapshotCreated) {
        this.recordSnapshotCreated = recordSnapshotCreated;
    }

    public URI getRemoteContextURI() {
        return remoteContextURI;
    }

    public void setRemoteContextURI(URI remoteContextURI) {
        this.remoteContextURI = remoteContextURI;
    }

    public Set<SubmittedAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<SubmittedAnswer> answers) {
        this.answers = answers;
    }

    public Record getRecord() {
        return record;
    }

    public void setRecord(Record record) {
        this.record = record;
    }

    public Integer getNumberOfAnswers() {
        return numberOfAnswers;
    }

    public void setNumberOfAnswers(Integer numberOfAnswers) {
        this.numberOfAnswers = numberOfAnswers;
    }
}

