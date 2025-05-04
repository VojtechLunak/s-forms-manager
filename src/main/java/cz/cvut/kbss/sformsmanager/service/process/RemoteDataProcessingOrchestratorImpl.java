package cz.cvut.kbss.sformsmanager.service.process;

import cz.cvut.kbss.sformsmanager.model.Vocabulary;
import cz.cvut.kbss.sformsmanager.model.persisted.local.*;
import cz.cvut.kbss.sformsmanager.model.persisted.local.Record;
import cz.cvut.kbss.sformsmanager.model.persisted.response.QuestionSnapshotRemoteData;
import cz.cvut.kbss.sformsmanager.model.persisted.response.RecordSnapshotRemoteData;
import cz.cvut.kbss.sformsmanager.persistence.dao.local.*;
import cz.cvut.kbss.sformsmanager.persistence.dao.local.custom.FormGenCacheQuestionDAO;
import cz.cvut.kbss.sformsmanager.persistence.dao.remote.RecordSnapshotRemoteDAO;
import cz.cvut.kbss.sformsmanager.service.formgen.FormGenCachedService;
import cz.cvut.kbss.sformsmanager.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RemoteDataProcessingOrchestratorImpl implements RemoteDataProcessingOrchestrator {

    private final RecordDAO recordDAO;
    private final RecordSnapshotRemoteDAO recordRemoteDAO;
    private final RecordSnapshotDAO recordSnapshotDAO;

    private final FormTemplateVersionDAO formTemplateVersionDAO;

    private final QuestionTemplateSnapshotDAO questionTemplateSnapshotDAO;
    private final QuestionTemplateDAO questionTemplateDAO;
    private final FormGenCacheQuestionDAO formGenCacheQuestionDAO;
    private final FormTemplateDAO formTemplateDAO;
    private final SubmittedAnswerDAO submittedAnswerDAO;
    private final RecordVersionDAO recordVersionDAO;
    private final FormGenCachedService formGenCachedService;

    @Autowired
    public RemoteDataProcessingOrchestratorImpl(
            RecordDAO recordDAO, QuestionTemplateSnapshotDAO questionTemplateSnapshotDAO,
            RecordSnapshotRemoteDAO recordRemoteDAO,
            RecordSnapshotDAO recordSnapshotDAO,
            FormTemplateVersionDAO formTemplateVersionDAO,
            QuestionTemplateDAO questionTemplateDAO, FormGenCacheQuestionDAO formGenCacheQuestionDAO, FormTemplateDAO formTemplateDAO,
            SubmittedAnswerDAO submittedAnswerDAO,
            RecordVersionDAO recordVersionDAO, FormGenCachedService formGenCachedService) {

        this.recordDAO = recordDAO;
        this.questionTemplateSnapshotDAO = questionTemplateSnapshotDAO;
        this.recordRemoteDAO = recordRemoteDAO;
        this.recordSnapshotDAO = recordSnapshotDAO;
        this.formTemplateVersionDAO = formTemplateVersionDAO;
        this.questionTemplateDAO = questionTemplateDAO;
        this.formGenCacheQuestionDAO = formGenCacheQuestionDAO;
        this.formTemplateDAO = formTemplateDAO;
        this.submittedAnswerDAO = submittedAnswerDAO;
        this.recordVersionDAO = recordVersionDAO;
        this.formGenCachedService = formGenCachedService;
    }

    @Transactional
    public void processDataSnapshotInRemoteContext(String projectName, URI contextUri) throws IOException, URISyntaxException {
        // cache formGen data locally first
        formGenCachedService.getFormGenRawJson(projectName, contextUri);

        // LOAD REMOTE DATA: record snapshot
        RecordSnapshotRemoteData recordRemoteData = recordRemoteDAO.getRecordSnapshot(projectName, contextUri);
        String formTemplateVersionString = recordRemoteData.getFormTemplateVersion();
        String recordSnapshotKey = ObjectUtils.createKeyForContext(projectName, contextUri);

        // Record
        String recordKey = ObjectUtils.createKeyForContext(projectName, recordRemoteData.getRemoteRecordURI().toString());
        Optional<Record> recordOpt = recordDAO.findByKey(projectName, recordKey);
        Record record;
        if (recordOpt.isPresent()) {
            record = recordOpt.get();
        } else {
            record = recordDAO.update(projectName, new Record(recordKey, new HashSet<>(), new HashSet<>(), null, recordRemoteData.getRecordCreateDate(), contextUri.toString()));
        }

        if (recordRemoteData.getRootQuestionOrigin() == null) {
            // initial record: no questions or answers variant

            // persist without RecordVersion and without FormTemplateVersion
            RecordSnapshot recordSnapshot = new RecordSnapshot(recordSnapshotKey, record, null, null, null, recordRemoteData.getRecordModifiedDate(), contextUri, 0);
            recordSnapshotDAO.persist(projectName, recordSnapshot);

            // re-update record
            record.getRecordSnapshots().add(recordSnapshot);
            recordDAO.update(projectName, record);
            return;
        }

        // LOAD FORM-GEN DATA from LOCAL CACHE: question and answers snapshot
        QuestionSnapshotRemoteData qaRemoteData = formGenCacheQuestionDAO.getQuestionsAndAnswersSnapshot(projectName, contextUri, recordRemoteData.getRootQuestionOrigin());

        // PROCESS the questions tree
        RemoteQuestionModelProcessor processor = new RemoteQuestionModelProcessor(qaRemoteData);
        processor.process();

        // Answers
        Map<String, SubmittedAnswer> submittedAnswerMap = mapToSubmittedAnswersAndPersistEventually(projectName, processor.getAnsweredQuestions(), processor.getQuestionOriginLabels());

        // FormTemplate
        String formTemplateKey = ObjectUtils.createKeyForContext(projectName, processor.getRootQuestionOrigin());
        Optional<FormTemplate> formTemplateOpt = formTemplateDAO.findByKey(projectName, formTemplateKey);

        FormTemplate formTemplate;
        if (formTemplateOpt.isPresent()) {
            formTemplate = formTemplateOpt.get();
        } else {
            formTemplate = new FormTemplate(formTemplateKey);
            formTemplate = formTemplateDAO.update(projectName, formTemplate);
        }

        // QuestionTemplate

        // FormTemplateVersion
        String formTemplateVersionKey = ObjectUtils.createKeyForContext(projectName, processor.getAllQuestionOriginsPathsHash());
        Optional<FormTemplateVersion> formTemplateVersionOpt = formTemplateVersionDAO.findByKey(projectName, formTemplateVersionKey);

        FormTemplateVersion formTemplateVersion;
        if (formTemplateVersionOpt.isPresent()) {
            formTemplateVersion = formTemplateVersionOpt.get();

            // add new answers to question template snapshots and update them directly
            addSubmittedAnswersToQuestionsAndPersist(projectName, formTemplateVersion, submittedAnswerMap);
        } else {
            formTemplateVersion = new FormTemplateVersion(formTemplateVersionKey, formTemplate, null, null, contextUri, formTemplateVersionString);
            formTemplateVersion.setInternalName(formTemplateVersionString.substring(formTemplateVersionString.lastIndexOf("/") + 1));
            QuestionTemplateSnapshot questionTemplateSnapshot = createQuestionTemplateSnapshotsWithAnswers(
                    processor.getQuestionOriginsAndTheirPaths(),
                    submittedAnswerMap,
                    qaRemoteData,
                    formTemplate,
                    formTemplateVersion,
                    projectName);
            formTemplateVersion.setQuestionTemplateSnapshot(questionTemplateSnapshot);
            formTemplateVersion = formTemplateVersionDAO.update(projectName, formTemplateVersion);
        }

        // RecordVersion
        String recordVersionKey = ObjectUtils.createKeyForContext(projectName, record.getUri() + "/" + processor.getAllQuestionOriginsAndAnswersString());
        Optional<RecordVersion> recordVersionOpt = recordVersionDAO.findByKey(projectName, recordVersionKey);

        RecordVersion recordVersion;
        if (recordVersionOpt.isPresent()) {
            recordVersion = recordVersionOpt.get();
        } else {
            recordVersion = new RecordVersion(recordVersionKey, record);
            recordVersion = recordVersionDAO.update(projectName, recordVersion);
        }

        // RecordSnapshot
        RecordSnapshot recordSnapshot = new RecordSnapshot(
                recordSnapshotKey,
                record,
                recordVersion,
                formTemplateVersion,
                new HashSet<>(submittedAnswerMap.values()),
                recordRemoteData.getRecordModifiedDate(),
                contextUri,
                processor.getAnsweredQuestions().values().size());

        // re-update Record
        record.setFormTemplate(formTemplate);
        record.getRecordSnapshots().add(recordSnapshot);
        record.getRecordVersions().add(recordVersion);
        record = recordDAO.update(projectName, record);
    }

    private QuestionTemplateSnapshot createQuestionTemplateSnapshotsWithAnswers(
            Map<String, String> questionOriginAndTheirPaths,
            Map<String, SubmittedAnswer> submittedAnswerMap,
            QuestionSnapshotRemoteData questionTreeRemoteData,
            FormTemplate formTemplate,
            FormTemplateVersion formTemplateVersion,
            String projectDescriptorName) {

        // takes existing FormTemplate question templates and connects them to the new questions

        // QuestionTemplate(s)
        Map<String, QuestionTemplate> questionTemplatesMap = questionTemplateDAO.findAllWhere(projectDescriptorName, Vocabulary.p_hasFormTemplate, formTemplate.getUri()).stream()
                .collect(Collectors.toMap(qt -> qt.getQuestionOrigin(), Function.identity()));

        LocalQuestionModelTreeBuilder qtsTreeBuilder = new LocalQuestionModelTreeBuilder(
                questionTreeRemoteData,
                questionTemplatesMap,
                questionOriginAndTheirPaths,
                formTemplate,
                formTemplateVersion,
                submittedAnswerMap,
                projectDescriptorName);

        return qtsTreeBuilder.process();
    }

    private void addSubmittedAnswersToQuestionsAndPersist(String projectDescriptorName, FormTemplateVersion formTemplateVersion, Map<String, SubmittedAnswer> submittedAnswerMap) {
        // QuestionTemplateSnapshot(s)
        List<QuestionTemplateSnapshot> questionTemplateSnapshots = questionTemplateSnapshotDAO.findAllWhere(projectDescriptorName, Vocabulary.p_hasFormTemplateVersion, formTemplateVersion.getUri());
        questionTemplateSnapshots.stream().forEach(qts -> {
            SubmittedAnswer sa;
            if ((sa = submittedAnswerMap.get(qts.getQuestionOrigin())) != null) {
                if (qts.getAnswers() != null) {
                    boolean added = qts.getAnswers().add(sa);
                    if (added) questionTemplateSnapshotDAO.update(projectDescriptorName, qts);
                } else {
                    Set<SubmittedAnswer> answers = new HashSet<>();
                    answers.add(sa);
                    qts.setAnswers(answers);
                    questionTemplateSnapshotDAO.update(projectDescriptorName, qts);
                }
            }
        });
    }

    private Map<String, SubmittedAnswer> mapToSubmittedAnswersAndPersistEventually(String projectDescriptorName, Map<String, String> answeredQuestions, Map<String, String> questionOriginLabels) {
        // the result is question-origin-1 -> SubmittedAnswer(with question-origin-1)
        return answeredQuestions.entrySet().stream().map(entry -> {
            String answerKey = ObjectUtils.createKeyForContext(projectDescriptorName, entry.getKey() + entry.getValue()); // question-origin + answer-value
            return submittedAnswerDAO.findByKey(projectDescriptorName, answerKey)
                    .orElseGet(() -> submittedAnswerDAO.update(projectDescriptorName, new SubmittedAnswer(answerKey, entry.getValue(), entry.getKey(), questionOriginLabels.get(entry.getKey()))));
        }).collect(Collectors.toMap(SubmittedAnswer::getQuestionOrigin, Function.identity()));
    }
}
