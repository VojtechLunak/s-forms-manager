package cz.cvut.kbss.sformsmanager.model.persisted.local;


import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.sformsmanager.model.Vocabulary;

import java.io.Serializable;
import java.net.URI;

@OWLClass(iri = Vocabulary.FormTemplateVersion)
public class FormTemplateVersion extends LocalEntity implements Serializable {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.p_hasFormTemplate, fetch = FetchType.EAGER)
    private FormTemplate formTemplate;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.p_hasFormTemplateVersionRecordManager)
    private String formTemplateVersion;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.p_hasQuestionTemplateSnapshot, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private QuestionTemplateSnapshot questionTemplateSnapshot;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.p_hasRemoteContextURI)
    private String sampleRemoteContextURI; // TODO: when this is URI, it's throwing unsupported literal type: java.net.URI

    @OWLDataProperty(iri = Vocabulary.p_internalName)
    private String internalName;

    // key is sorted all the QUESTION-ORIGINS -> hash

    public FormTemplateVersion() {
    }

    public FormTemplateVersion(String questionOriginsHash, FormTemplate formTemplate, QuestionTemplateSnapshot questionTemplateSnapshot, String internalName, URI sampleRemoteContextURI, String formTemplateVersionRM) {
        super(questionOriginsHash);
        this.formTemplate = formTemplate;
        this.questionTemplateSnapshot = questionTemplateSnapshot;
        this.internalName = internalName;
        this.sampleRemoteContextURI = sampleRemoteContextURI.toString();
        this.formTemplateVersion = formTemplateVersionRM;
    }

    public QuestionTemplateSnapshot getQuestionTemplateSnapshot() {
        return questionTemplateSnapshot;
    }

    public void setQuestionTemplateSnapshot(QuestionTemplateSnapshot questionTemplateSnapshot) {
        this.questionTemplateSnapshot = questionTemplateSnapshot;
    }

    public FormTemplate getFormTemplate() {
        return formTemplate;
    }

    public void setFormTemplate(FormTemplate formTemplate) {
        this.formTemplate = formTemplate;
    }

    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        this.internalName = internalName;
    }

    public String getSampleRemoteContextURI() {
        return sampleRemoteContextURI;
    }

    public void setSampleRemoteContextURI(URI sampleRemoteContextURI) {
        this.sampleRemoteContextURI = sampleRemoteContextURI.toString();
    }

    public String getFormTemplateVersion() {
        return formTemplateVersion;
    }

    public void setFormTemplateVersion(String formTemplateVersion) {
        this.formTemplateVersion = formTemplateVersion;
    }
}
