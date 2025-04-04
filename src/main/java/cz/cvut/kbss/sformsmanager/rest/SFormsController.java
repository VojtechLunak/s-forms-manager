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

import cz.cvut.kbss.sformsmanager.service.formgen.FormGenCachedService;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/sforms")
public class SFormsController {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SFormsController.class);

    private final FormGenCachedService formGenCachedService;

    @Autowired
    public SFormsController(FormGenCachedService formGenCachedService) {
        this.formGenCachedService = formGenCachedService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-json-ld")
    public String getFormGenRawJson(
            @RequestParam(value = "projectName") String projectName,
            @RequestParam(value = "contextUri") String contextUri
    ) throws URISyntaxException, IOException {
        return formGenCachedService.getFormGenRawJson(projectName, URI.create(contextUri)).getRawJson();
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
            @PathVariable(value = "contextUri") String contextUri
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not Implemented");
    }

    @RequestMapping(method = RequestMethod.POST, path = "s-forms-possible-values")
    public String getFormGenRawJson(
            @RequestParam(value = "query") String query
    ) throws URISyntaxException {
        return formGenCachedService.getFormGenPossibleValues(query);
    }
}
