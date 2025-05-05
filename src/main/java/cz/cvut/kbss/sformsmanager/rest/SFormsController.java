/**
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.sformsmanager.rest;

import cz.cvut.kbss.sformsmanager.model.persisted.local.Project;
import cz.cvut.kbss.sformsmanager.service.formgen.FormGenCachedService;
import cz.cvut.kbss.sformsmanager.service.formgen.FormTemplateExtractionService;
import cz.cvut.kbss.sformsmanager.service.model.local.ProjectService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

@RestController
@RequestMapping("/sforms")
public class SFormsController {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SFormsController.class);

    private final FormGenCachedService formGenCachedService;
    private final FormTemplateExtractionService formTemplateExtractionService;
    private final ProjectService projectService;

    @Autowired
    public SFormsController(FormGenCachedService formGenCachedService, FormTemplateExtractionService formTemplateExtractionService, ProjectService projectService) {
        this.formGenCachedService = formGenCachedService;
        this.formTemplateExtractionService = formTemplateExtractionService;
        this.projectService = projectService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-json-ld")
    public String getFormGenRawJson(
            @RequestParam(value = "projectName") String projectName,
            @RequestParam(value = "contextUri") String contextUri
    ) throws URISyntaxException, IOException {
        return formGenCachedService.getFormGenRawJson(projectName, URI.create(contextUri)).getRawJson();
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-json-ld/version")
    public String getFormGenRawJsonForVersion(
            @RequestParam(value = "projectName") String projectName,
            @RequestParam(value = "contextUri") String contextUri,
            @RequestParam(value = "version") String formTemplateVersion
    ) throws URISyntaxException, IOException {
        String versionUri = "https://example.org/sfc-example-1/form-root/" + formTemplateVersion;
        return formGenCachedService.getFormGenRawJson(projectName, URI.create(contextUri), versionUri).getRawJson();
    }

    @RequestMapping(method = RequestMethod.GET, path = "s-forms-json-ld/{projectName}/{contextUri}")
    public String getFormGenRawJsonGet(
            @PathVariable(value = "projectName") String projectName,
            @PathVariable(value = "contextUri") String contextUri
    ) throws URISyntaxException, IOException {
        String contextUriBase = "http://onto.fel.cvut.cz/ontologies/record-manager/";
        contextUri = contextUriBase + contextUri;
        return formGenCachedService.getFormGenRawJson(projectName, URI.create(contextUri)).getRawJson();
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-json-ld/{projectName}/{contextUri}")
    public ResponseEntity<String> getFormGenRawJsonPost(
            @PathVariable(value = "projectName") String projectName,
            @PathVariable(value = "contextUri") String contextUri,
            @RequestBody String jsonLdData
    ) {
        Project project = projectService.findByKey(projectName).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND)
        );
        String baseGraphDbUrl = project.getFormGenRepositoryUrl() + "/statements";

        try {
            String formTemplateJsonLdString = formTemplateExtractionService.extractFormTemplateFromFormData(jsonLdData, project.getFormGenRepositoryUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/ld+json"));
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<String> request = new HttpEntity<>(formTemplateJsonLdString, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    baseGraphDbUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return ResponseEntity.status(HttpStatus.OK).body("New form template version uploaded successfully to: " + project.getFormGenRepositoryUrl());
        } catch (Exception e) {
            log.error("Failed to upload form template to GraphDB", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading form template to GraphDB.");
        }
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-possible-values")
    public String getFormGenRawJson(
            @RequestParam(value = "query") String query
    ) throws URISyntaxException {
        return formGenCachedService.getFormGenPossibleValues(query);
    }
}
