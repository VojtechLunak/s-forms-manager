package cz.cvut.kbss.sformsmanager.service.formgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.sformsmanager.model.dto.SFormsRawJson;
import cz.cvut.kbss.sformsmanager.model.persisted.local.Project;
import cz.cvut.kbss.sformsmanager.persistence.dao.local.ProjectDAO;
import cz.cvut.kbss.sformsmanager.service.data.RemoteDataLoader;
import cz.cvut.kbss.sformsmanager.utils.RecordPhase;
import org.eclipse.rdf4j.repository.Repository;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RemoteFormGenJsonLoader implements FormGenJsonLoader {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RemoteFormGenJsonLoader.class);

    private final RemoteDataLoader dataLoader;
    private final ProjectDAO projectDAO;
    private final Repository repository;

    private static final String REPOSITORY_URL_PARAM = "repositoryUrl";
    private static final String FORMGEN_REPOSITORY_URL_PARAM = "formGenRepositoryUrl";
    private static final String RECORD_GRAPH_ID_PARAM = "recordGraphId";
    private static final String FORM_TEMPLATE_VERSION = "formTemplateVersion";
    private static final String IGNORE_INVALID_DATA = "isIgnoreInvalidData";

    @Value("${RM_BACKEND_API_URL:http://localhost:1235/services/record-manager-server}")
    private String RECORD_MANAGER_API_URL;

    @Autowired
    public RemoteFormGenJsonLoader(RemoteDataLoader dataLoader, ProjectDAO projectDAO, Repository repository) {
        this.dataLoader = dataLoader;
        this.projectDAO = projectDAO;
        this.repository = repository;
    }

    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, boolean ignoreInvalidData) throws URISyntaxException {
        final Map<String, String> params = new HashMap<>();
        params.put(IGNORE_INVALID_DATA, String.valueOf(ignoreInvalidData));
        return this.getFormGenRawJson(projectName, contextUri.toString(), params);
    }

    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, String version) throws URISyntaxException {
        final Map<String, String> params = new HashMap<>();
        params.put(FORM_TEMPLATE_VERSION, version);
        params.put(IGNORE_INVALID_DATA, "true");
        return this.getFormGenRawJson(projectName, contextUri.toString(), params);
    }

    /**
     * Service for generating forms at a 'connected repository'.
     *
     * @param projectName
     * @param contextUri
     * @return
     * @throws URISyntaxException
     */
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri) throws URISyntaxException {
        Project project = projectDAO.findByKey(projectName, projectName).orElseThrow(
                () -> new RuntimeException(String.format("Repository connection with project descriptor '%s' does not exist.", projectName)));

        final Map<String, String> params = new HashMap<>();
        params.put(RECORD_GRAPH_ID_PARAM, contextUri.toString());
        params.put(REPOSITORY_URL_PARAM, project.getAppRepositoryUrl());
        params.put(FORMGEN_REPOSITORY_URL_PARAM, project.getFormGenRepositoryUrl());

        log.info("Trying to get raw JSONLD data from {}, formgen: {}", project.getAppRepositoryUrl(), project.getFormGenRepositoryUrl());

        if(Objects.equals(project.getAppRepositoryUrl(), "http://localhost:1235/services/db-server/repositories/record-manager-app")) {
            params.put(REPOSITORY_URL_PARAM, "http://db-server:7200/repositories/record-manager-app");
        }
        if(Objects.equals(project.getFormGenRepositoryUrl(), "http://localhost:1235/services/db-server/repositories/record-manager-formgen")) {
            params.put(FORMGEN_REPOSITORY_URL_PARAM, "http://db-server:7200/repositories/record-manager-formgen");
        }



        String rawFormJson = dataLoader.loadDataFromUrl(project.getFormGenServiceUrl(), params, Collections.emptyMap());
        return new SFormsRawJson(projectName, contextUri.toString(), rawFormJson);
    }

    private SFormsRawJson getFormGenRawJson(String projectName, String contextUri, Map<String, String> passedParams) throws URISyntaxException {
        Project project = projectDAO.findByKey(projectName, projectName).orElseThrow(
                () -> new RuntimeException(String.format("Repository connection with project descriptor '%s' does not exist.", projectName)));

        final Map<String, String> params = new HashMap<>();
        params.put(RECORD_GRAPH_ID_PARAM, contextUri);
        params.put(REPOSITORY_URL_PARAM, project.getAppRepositoryUrl());
        params.put(FORMGEN_REPOSITORY_URL_PARAM, project.getFormGenRepositoryUrl());
        params.putAll(passedParams);

        log.info("Trying to get raw JSONLD data from {}, formgen: {}", project.getAppRepositoryUrl(), project.getFormGenRepositoryUrl());

        if(Objects.equals(project.getAppRepositoryUrl(), "http://localhost:1235/services/db-server/repositories/record-manager-app")) {
            params.put(REPOSITORY_URL_PARAM, "http://db-server:7200/repositories/record-manager-app");
        }
        if(Objects.equals(project.getFormGenRepositoryUrl(), "http://localhost:1235/services/db-server/repositories/record-manager-formgen")) {
            params.put(FORMGEN_REPOSITORY_URL_PARAM, "http://db-server:7200/repositories/record-manager-formgen");
        }

        String rawFormJson = dataLoader.loadDataFromUrl(project.getFormGenServiceUrl(), params, Collections.emptyMap());
        return new SFormsRawJson(project.getKey(), contextUri, rawFormJson);
    }

    public String exportGraph(String contextUri, String sourceRepoUrl) {
        String sparql = """
        CONSTRUCT { ?s ?p ?o }
        WHERE { GRAPH <%s> { ?s ?p ?o } }
        """.formatted(contextUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-query"));
        headers.set("Accept", "application/ld+json");

        HttpEntity<String> request = new HttpEntity<>(sparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                sourceRepoUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        return response.getBody(); // This is the full JSON-LD content
    }

    public void importGraph(String contextUri, String targetRepoUrl, String jsonLd) {
        HttpHeaders headers = new HttpHeaders();
        targetRepoUrl = targetRepoUrl + "/statements";
        headers.setContentType(MediaType.parseMediaType("application/ld+json"));
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> request = new HttpEntity<>(jsonLd, headers);
        RestTemplate restTemplate = new RestTemplate();

        String grahpUri = "<" + contextUri + ">";

        restTemplate.exchange(
                targetRepoUrl + "?context=" + grahpUri,
                HttpMethod.POST,
                request,
                String.class
        );
    }

    public void deleteGraph(String contextUri, String repoUrl) {
        HttpHeaders headers = new HttpHeaders();
        RestTemplate restTemplate = new RestTemplate();
        repoUrl = repoUrl + "/statements";
        headers.setContentType(MediaType.valueOf("application/sparql-update"));
        String deleteQuery = "CLEAR GRAPH <" + contextUri + ">";
        restTemplate.exchange(repoUrl, HttpMethod.POST, new HttpEntity<>(deleteQuery, headers), String.class);
    }


    public String getFormStructure(String formGenUri, Project project) throws URISyntaxException {
        final Map<String, String> params = new HashMap<>();
        params.put(RECORD_GRAPH_ID_PARAM, formGenUri);
        params.put(REPOSITORY_URL_PARAM, project.getAppRepositoryUrl());
        params.put(FORMGEN_REPOSITORY_URL_PARAM, project.getFormGenRepositoryUrl());
        return dataLoader.loadDataFromUrl(project.getFormGenServiceUrl(), params, Collections.emptyMap());
    }

    private HttpHeaders processHeaders(Map<String, String> params) {
        final HttpHeaders headers = new HttpHeaders();
        // Set default accept type to JSON-LD
        headers.set(HttpHeaders.ACCEPT, "application/ld+json");
        headers.set(HttpHeaders.COOKIE, params.get("sessionCookie"));
        String[] supportedHeaders = {HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE};
        for (String header : supportedHeaders) {
            if (params.containsKey(header)) {
                headers.set(header, params.get(header));
                params.remove(header);
            }
        }
        return headers;
    }

    public String getFormGenPossibleValues(String query) throws URISyntaxException {
        return dataLoader.loadDataFromUrl(query, Collections.emptyMap(), Collections.emptyMap());
    }

    public void generateVirtualFormGen(String recordUri, String formGenUri, String appRepoUrl) {
        String sparql = generateFormGenInsertQuery(recordUri, formGenUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-update"));
        HttpEntity<String> sparqlRequest = new HttpEntity<>(sparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.exchange(appRepoUrl + "/statements",
                HttpMethod.POST, sparqlRequest, String.class);
    }

    private String generateFormGenInsertQuery(String recordUri, String formGenUri) {
        return String.format("""
        PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>
        PREFIX doc: <http://onto.fel.cvut.cz/ontologies/documentation/>
        PREFIX form: <http://onto.fel.cvut.cz/ontologies/form/>
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>

        INSERT {
          GRAPH <%s> {
            ?record ?p ?o .
            ?author ?pa ?oa .
            ?inst ?pi ?oi .
            ?q ?qp ?qo .
            ?a ?ap ?ao .
          }
        }
        WHERE {
          BIND(<%s> AS ?record)
          ?record ?p ?o .

          OPTIONAL {
            ?record srm:has-author ?author .
            ?author ?pa ?oa .
          }

          OPTIONAL {
            ?record srm:was-treated-at ?inst .
            ?inst ?pi ?oi .
          }

          OPTIONAL {
            ?record srm:has-question ?rootQ .
            ?rootQ <http://onto.fel.cvut.cz/ontologies/documentation/has_related_question>* ?q .
            ?q ?qp ?qo .

            OPTIONAL {
              ?q <http://onto.fel.cvut.cz/ontologies/documentation/has_answer> ?a .
              ?a ?ap ?ao .
            }
          }
        }
        """, formGenUri, recordUri);
    }

    public Map<String, String> getRecordMetadata(String formGenUri, String recordUri, String formGenRepoUrl) {
        String sparql = String.format("""
        PREFIX dcterms: <http://purl.org/dc/terms/>
        PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>
        PREFIX foaf: <http://xmlns.com/foaf/0.1/>
        
        SELECT ?label ?created ?modified ?phase ?rejectReason ?authorEmail ?version
        WHERE {
          GRAPH <%s> {
            BIND (<%s> AS ?record)
            ?record <http://onto.fel.cvut.cz/ontologies/record-manager/has-author> ?author .
            ?author foaf:mbox ?authorEmail .
            ?record rdfs:label ?label .
            ?record dcterms:created ?created .
            OPTIONAL { ?record dcterms:modified ?modified }
            ?record srm:has-phase ?phase .
            OPTIONAL { ?record srm:reject-reason ?rejectReason }
            OPTIONAL { ?record srm:has-form-template-version ?version }
          }
        }
        """, formGenUri, recordUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-query"));
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> request = new HttpEntity<>(sparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                formGenRepoUrl,
                HttpMethod.POST,
                request,
                String.class
        );

        Map<String, String> resultMap = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode bindings = root.get("results").get("bindings").get(0);

            // Parse and format the "label" value
            if (bindings.has("label")) {
                resultMap.put("Label", bindings.get("label").get("value").asText());
            }

            // Parse and format the "created" value
            if (bindings.has("created")) {
                String createdRaw = bindings.get("created").get("value").asText();
                ZonedDateTime createdDateTime = ZonedDateTime.parse(createdRaw).withZoneSameInstant(ZoneId.systemDefault());
                String formattedCreated = createdDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss z", Locale.getDefault()));
                resultMap.put("Created", formattedCreated);
            }

            // Parse and format the "modified" value
            if (bindings.has("modified")) {
                String modifiedRaw = bindings.get("modified").get("value").asText();
                ZonedDateTime modifiedDateTime = ZonedDateTime.parse(modifiedRaw).withZoneSameInstant(ZoneId.systemDefault());
                String formattedModified = modifiedDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss z", Locale.getDefault()));
                resultMap.put("Modified", formattedModified);
            }
            if (bindings.has("phase")) resultMap.put("Phase", bindings.get("phase").get("value").asText());
            if (bindings.has("rejectReason")) resultMap.put("Reject Reason", "\n"+ bindings.get("rejectReason").get("value").asText());
            if (bindings.has("authorEmail")) resultMap.put("Email", bindings.get("authorEmail").get("value").asText());

            if (bindings.has("version")) resultMap.put("Version", bindings.get("version").get("value").asText());
        } catch (Exception e) {
            log.warn("Failed to parse metadata SPARQL result", e);
        }

        return resultMap;
    }

    public void changeRecordPhaseForFormGenSPARQL(String formGen, RecordPhase recordPhase, String formGenRepoUrl, String appRepoUrl) {
        String getRecordSparql = String.format("""
                PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>
                
                SELECT ?record
                WHERE {
                  GRAPH <%s> {
                    ?record a srm:patient-record .
                  }
                }
                """, formGen);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-query"));
        HttpEntity<String> sparqlRequest = new HttpEntity<>(getRecordSparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                formGenRepoUrl,
                HttpMethod.POST,
                sparqlRequest,
                String.class
        );

        String recordIRISparql = "";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode bindings = root.get("results").get("bindings").get(0);
            if (bindings.has("record")) recordIRISparql = bindings.get("record").get("value").asText();
        } catch (Exception e) {
            log.warn("Failed to parse metadata SPARQL result", e);
        }

        String changeRecordPhaseSparql = String.format("""
                PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>
                
                 WITH <%s>
                 DELETE {
                     <%s> srm:has-phase ?phase .
                 }
                 INSERT {
                     <%s> srm:has-phase <%s> .
                 }
                 WHERE {
                     <%s> a srm:patient-record .
                     VALUES ?phase {
                       <%s>
                       <%s>
                       <%s>
                     }
                 }
                """, recordIRISparql, recordIRISparql, recordIRISparql, recordPhase.toString(), recordIRISparql, RecordPhase.REJECTED, RecordPhase.OPEN, RecordPhase.COMPLETE);

        headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-update"));
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> sparqlRequest2 = new HttpEntity<>(changeRecordPhaseSparql, headers);
        restTemplate.exchange(
                appRepoUrl + "/statements",
                HttpMethod.POST,
                sparqlRequest2,
                String.class
        );

        // refresh the original record - workaround for cache settings of JOPA regarding PersistenceContext in RM
        String exportedRecord = this.exportGraph(recordIRISparql, appRepoUrl);
        this.deleteGraph(recordIRISparql, appRepoUrl);
        this.importGraph(recordIRISparql, appRepoUrl, exportedRecord);
    }

    public void changeRecordPhaseForAllRecordsSPARQL(RecordPhase recordPhase, String appRepositoryUrl) {
        String changeRecordPhaseSparql = String.format("""
                PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>

                DELETE {
                    GRAPH ?g {
                        ?record srm:has-phase ?phase .
                    }
                }
                INSERT {
                    GRAPH ?g {
                        ?record srm:has-phase <%s> .
                    }
                }
                WHERE {
                    GRAPH ?g {
                        ?record a srm:patient-record .
                        ?record srm:has-phase ?phase .
                    }
                    VALUES ?phase {
                        <%s>
                        <%s>
                        <%s>
                    }
                }
            """, recordPhase.toString(), RecordPhase.REJECTED, RecordPhase.OPEN, RecordPhase.COMPLETE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-update"));
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> sparqlRequest = new HttpEntity<>(changeRecordPhaseSparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.exchange(
                appRepositoryUrl + "/statements",
                HttpMethod.POST,
                sparqlRequest,
                String.class
        );
    }

    public void changeRecordPhaseForAllRecords(RecordPhase recordPhase) {
        RestTemplate restTemplate = new RestTemplate();
        String operationUrl = RECORD_MANAGER_API_URL + "/rest/external/open/all";
        restTemplate.getForObject(
                operationUrl,
                String.class
        );
    }

    public void changeRecordPhaseForFormGen(String formGenUri, RecordPhase recordPhase, String formGenRepoUrl) {
        String getRecordSparql = String.format("""
                PREFIX srm: <http://onto.fel.cvut.cz/ontologies/record-manager/>
                
                SELECT ?record
                WHERE {
                  GRAPH <%s> {
                    ?record a srm:patient-record .
                  }
                }
                """, formGenUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/sparql-query"));
        HttpEntity<String> sparqlRequest = new HttpEntity<>(getRecordSparql, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                formGenRepoUrl,
                HttpMethod.POST,
                sparqlRequest,
                String.class
        );

        String recordIRISparql = "";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode bindings = root.get("results").get("bindings").get(0);
            if (bindings.has("record")) recordIRISparql = bindings.get("record").get("value").asText();
        } catch (Exception e) {
            log.warn("Failed to parse metadata SPARQL result", e);
        }

        String operationUrl = RECORD_MANAGER_API_URL + "/rest/external/open/" + recordIRISparql.substring(recordIRISparql.lastIndexOf("/") + 1);
        restTemplate.getForEntity(
                operationUrl,
                String.class
        );
    }
}