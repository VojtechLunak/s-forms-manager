package cz.cvut.kbss.sformsmanager.service.formgen;

import cz.cvut.kbss.sformsmanager.model.dto.SFormsRawJson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public interface FormGenJsonLoader {
    SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, boolean ignoreInvalidData) throws URISyntaxException, IOException;

    SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, String version) throws URISyntaxException, IOException;

    SFormsRawJson getFormGenRawJson(String projectName, URI contextUri) throws URISyntaxException, IOException;

    String getFormGenPossibleValues(String query) throws URISyntaxException;

}
