package cz.cvut.kbss.sformsmanager.service.formgen;

import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.sformsmanager.model.dto.SFormsRawJson;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.eclipse.rdf4j.rio.jsonld.JSONLDWriter;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Service
public class LocalFormGenJsonLoader implements FormGenJsonLoader {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LocalFormGenJsonLoader.class);

    private final Repository repository;

    private final RemoteFormGenJsonLoader remoteFormGenJsonLoader;

    @Autowired
    public LocalFormGenJsonLoader(Repository repository, RemoteFormGenJsonLoader remoteFormGenJsonLoader) {
        this.repository = repository;
        this.remoteFormGenJsonLoader = remoteFormGenJsonLoader;
    }

    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, boolean ignoreInvalidData) {
        return getFormGenRawJson(projectName, contextUri);
    }


    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri, String version) {
       return getFormGenRawJson(projectName, contextUri);
    }

    /**
     * Service for generating forms at a 'connected repository'.
     *
     * @param projectName
     * @param contextUri
     * @return
     * @throws URISyntaxException
     */
    @Override
    public SFormsRawJson getFormGenRawJson(String projectName, URI contextUri) {
        Optional<Resource> resource = getFormGenResource(projectName, contextUri);
//        if (!resource.isPresent()) {
        if (true) { // TODO: fix when caching is enabled
            return null;
        }

        RepositoryConnection connection = repository.getConnection();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JSONLDWriter writer = new JSONLDWriter(bos);

        WriterConfig config = new WriterConfig();
        config.set(JSONLDSettings.COMPACT_ARRAYS, true);
        config.set(JSONLDSettings.JSONLD_MODE, JSONLDMode.FLATTEN);

        connection.export(writer, resource.get());
        connection.close();

        return new SFormsRawJson(projectName, contextUri.toString(), new String(bos.toByteArray()));
    }

    @Override
    public String getFormGenPossibleValues(String query) {
        throw new UnsupportedOperationException("Caching possible values is not supported yet.");
    }

    public Optional<Resource> getFormGenResource(String projectName, URI contextUri) {
        return repository.getConnection().getContextIDs().stream()
                .filter(r -> r.stringValue().equals(createContextIdentifier(projectName, contextUri)))
                .findAny();
    }

    public Resource createFormGenSimpleResource(String projectName, URI contextUri) {
        return repository.getValueFactory().createIRI(createContextIdentifier(projectName, contextUri));
    }

    public static EntityDescriptor createContextDescriptor(String projectName, URI contextUri) {
        return new EntityDescriptor(URI.create(createContextIdentifier(projectName, contextUri)), false);
    }

    public static String createContextIdentifier(String projectName, URI contextUri) {
        return contextUri.toString() + "#" + projectName;
    }
}