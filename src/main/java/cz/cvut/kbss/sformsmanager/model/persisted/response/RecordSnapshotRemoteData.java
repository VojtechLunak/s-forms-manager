package cz.cvut.kbss.sformsmanager.model.persisted.response;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;

import java.net.URI;
import java.util.Date;

@SparqlResultSetMapping(name = "RecordSnapshotRemoteDataResponse",
        classes = {
                @ConstructorResult(targetClass = RecordSnapshotRemoteData.class, variables = {
                        @VariableResult(name = "recordCreateDate", type = Date.class),
                        @VariableResult(name = "recordModifiedDate", type = Date.class),
                        @VariableResult(name = "remoteRecordURI", type = URI.class),
                        @VariableResult(name = "rootQuestionOrigin", type = String.class),
                        @VariableResult(name= "formTemplateVersion", type = String.class)
                })
        })
public class RecordSnapshotRemoteData {
    private Date recordCreateDate;
    private Date recordModifiedDate; // record snapshot saved
    private URI remoteRecordURI; // context URI of the remote formGen
    private String rootQuestionOrigin; // is empty for the initial records
    private String formTemplateVersion; // version of the form template used for the record

    public RecordSnapshotRemoteData() {
    }

    public RecordSnapshotRemoteData(Date recordCreateDate, Date recordModifiedDate, URI remoteRecordURI, String rootQuestionOrigin, String formTemplateVersion) {
        this.recordCreateDate = recordCreateDate;
        this.recordModifiedDate = recordModifiedDate;
        this.remoteRecordURI = remoteRecordURI;
        this.rootQuestionOrigin = rootQuestionOrigin;
        this.formTemplateVersion = formTemplateVersion;
    }

    public String getFormTemplateVersion() {
        return formTemplateVersion;
    }

    public void setFormTemplateVersion(String formTemplateVersion) {
        this.formTemplateVersion = formTemplateVersion;
    }

    public Date getRecordCreateDate() {
        return recordCreateDate;
    }

    public void setRecordCreateDate(Date recordCreateDate) {
        this.recordCreateDate = recordCreateDate;
    }

    public Date getRecordModifiedDate() {
        return recordModifiedDate;
    }

    public void setRecordModifiedDate(Date recordModifiedDate) {
        this.recordModifiedDate = recordModifiedDate;
    }

    public URI getRemoteRecordURI() {
        return remoteRecordURI;
    }

    public void setRemoteRecordURI(URI remoteRecordURI) {
        this.remoteRecordURI = remoteRecordURI;
    }

    public String getRootQuestionOrigin() {
        return rootQuestionOrigin;
    }

    public void setRootQuestionOrigin(String rootQuestionOrigin) {
        this.rootQuestionOrigin = rootQuestionOrigin;
    }
}