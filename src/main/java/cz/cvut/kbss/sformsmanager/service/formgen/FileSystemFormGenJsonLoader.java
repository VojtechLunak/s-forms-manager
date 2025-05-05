package cz.cvut.kbss.sformsmanager.service.formgen;

import cz.cvut.kbss.sformsmanager.model.dto.SFormsRawJson;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import static cz.cvut.kbss.sformsmanager.persistence.dao.local.custom.CustomQueryDAO.CLASSPATH_PREFIX;

@Service
public class FileSystemFormGenJsonLoader implements FormGenJsonLoader {

    public final String FILE_SYSTEM_FORMGEN_FOLDER = CLASSPATH_PREFIX + "forms/";
    public final String CONTEXT_PREFIX = "http://vfn.cz/ontologies/study-manager/";
    public final String CONTEXT_SUFIX = "#mock";

    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, String version) throws IOException {
        String contextUriString = contextUri.toString();
        String fileName = contextUriString.replace(CONTEXT_PREFIX, "").replace(CONTEXT_SUFIX, "") + ".json";
        try {
            return new SFormsRawJson(projectName, contextUriString, new String(Files.readAllBytes(ResourceUtils.getFile(FILE_SYSTEM_FORMGEN_FOLDER + fileName).toPath())));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri) throws IOException {
        String contextUriString = contextUri.toString();
        String fileName = contextUriString.replace(CONTEXT_PREFIX, "").replace(CONTEXT_SUFIX, "") + ".json";
        try {
            return new SFormsRawJson(projectName, contextUriString, new String(Files.readAllBytes(ResourceUtils.getFile(FILE_SYSTEM_FORMGEN_FOLDER + fileName).toPath())));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getFormGenPossibleValues(String query) {
        return "";
    }
}
