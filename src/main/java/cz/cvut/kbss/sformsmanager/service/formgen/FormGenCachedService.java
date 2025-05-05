package cz.cvut.kbss.sformsmanager.service.formgen;

import cz.cvut.kbss.sformsmanager.model.dto.SFormsRawJson;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

@Service
public class FormGenCachedService implements FormGenJsonLoader {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(FormGenCachedService.class);

    @Value("${app.debug}")
    private boolean debug;

    private final LocalFormGenJsonLoader localFormGenJsonLoader;
    private final RemoteFormGenJsonLoader remoteFormGenJsonLoader;
    private final FileSystemFormGenJsonLoader fileSystemFormGenJsonLoader;

    private final Repository repository;

    @Autowired
    public FormGenCachedService(LocalFormGenJsonLoader localFormGenJsonLoader, RemoteFormGenJsonLoader remoteFormGenJsonLoader, FileSystemFormGenJsonLoader fileSystemFormGenJsonLoader, Repository repository) {
        this.localFormGenJsonLoader = localFormGenJsonLoader;
        this.remoteFormGenJsonLoader = remoteFormGenJsonLoader;
        this.fileSystemFormGenJsonLoader = fileSystemFormGenJsonLoader;
        this.repository = repository;
    }


    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, String version) throws URISyntaxException, IOException {
        SFormsRawJson localFormGen = localFormGenJsonLoader.getFormGenRawJson(projectName, contextUri, version);
        if (localFormGen != null) {
            // already cached
            return localFormGen;
        }

        SFormsRawJson formGen = null;
        if (debug) {
            log.trace("Trying to get MOCK data from file system. Project name: {}. ContextUri: {}.", projectName, contextUri.toString());
            formGen = fileSystemFormGenJsonLoader.getFormGenRawJson(projectName, contextUri, version);
        }

        if (formGen == null) {
            formGen = remoteFormGenJsonLoader.getFormGenRawJson(projectName, contextUri, version);
        }

        // save (cache) formGen to local repository
        Resource contextResource = localFormGenJsonLoader.createFormGenSimpleResource(projectName, contextUri);
        try {
            repository.getConnection().add(new StringReader(formGen.getRawJson()), null, RDFFormat.JSONLD, contextResource);
        } catch (RDFParseException e) {
            log.error("Error when saving formGen to local repository.", e);
        }

        return formGen;
    }

    /**
     * Loads formGen from local repository if it exists. If it does not, load it remotely and cache it.
     */
    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri) throws URISyntaxException, IOException {
        SFormsRawJson localFormGen = localFormGenJsonLoader.getFormGenRawJson(projectName, contextUri);
        if (localFormGen != null) {
            // already cached
            return localFormGen;
        }

        SFormsRawJson formGen = null;
        if (debug) {
            log.trace("Trying to get MOCK data from file system. Project name: {}. ContextUri: {}.", projectName, contextUri.toString());
            formGen = fileSystemFormGenJsonLoader.getFormGenRawJson(projectName, contextUri);
        }

        if (formGen == null) {
            formGen = remoteFormGenJsonLoader.getFormGenRawJson(projectName, contextUri);
        }

        // save (cache) formGen to local repository
        Resource contextResource = localFormGenJsonLoader.createFormGenSimpleResource(projectName, contextUri);
        try {
            repository.getConnection().add(new StringReader(formGen.getRawJson()), null, RDFFormat.JSONLD, contextResource);
        } catch (RDFParseException e) {
            log.error("Error when saving formGen to local repository.", e);
        }

        return formGen;
    }

    /**
     * Loads possible values from remote address.
     *
     * @param query
     * @return
     * @throws URISyntaxException
     */
    @Override
    public String getFormGenPossibleValues(String query) throws URISyntaxException {
        return remoteFormGenJsonLoader.getFormGenPossibleValues(query);
    }
}
