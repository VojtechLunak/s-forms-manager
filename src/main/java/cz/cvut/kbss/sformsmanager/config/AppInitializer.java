package cz.cvut.kbss.sformsmanager.config;

import cz.cvut.kbss.sformsmanager.model.persisted.local.Project;
import cz.cvut.kbss.sformsmanager.service.model.local.ProjectService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AppInitializer {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AppInitializer.class);
    private final ProjectService projectService;

    @Value("${DEFAULT_PROJECT_NAME:}")
    private String defaultProjectName;

    @Value("${APP_REPOSITORY_URL:}")
    private String defaultAppRepositoryUrl;

    @Value("${FORMGEN_REPOSITORY_URL:}")
    private String defaultFormGenRepositoryUrl;

    @Value("${SPIPES_SERVICE_URL:}")
    private String defaultSpipesServiceUrl;

    @Value("classpath:templates/remote/recordSnapshotRemoteData.sparql")
    private Resource defaultRecordRecognitionSparqlFile;

    private String defaultRecordRecognitionSparql;

    @Autowired
    public AppInitializer(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostConstruct
    public void initialize() throws IOException {
        if (defaultRecordRecognitionSparqlFile.exists()) {
            defaultRecordRecognitionSparql = Files.readString(Path.of(defaultRecordRecognitionSparqlFile.getURI()));
        }

        if (projectService.findAll().isEmpty()) {
            Project defaultProject = new Project(
                    defaultFormGenRepositoryUrl,
                    defaultSpipesServiceUrl,
                    defaultAppRepositoryUrl,
                    defaultProjectName,
                    defaultRecordRecognitionSparql
            );
            log.info("Creating default project: {}", defaultProject);
            projectService.create(defaultProject);
        }
    }
}