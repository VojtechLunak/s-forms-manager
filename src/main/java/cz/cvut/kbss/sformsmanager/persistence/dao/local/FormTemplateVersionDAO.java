package cz.cvut.kbss.sformsmanager.persistence.dao.local;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.sformsmanager.model.persisted.local.FormTemplateVersion;
import cz.cvut.kbss.sformsmanager.persistence.dao.LocalEntityBaseDAO;
import org.slf4j.Logger;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class FormTemplateVersionDAO extends LocalEntityBaseDAO<FormTemplateVersion> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(FormTemplateVersionDAO.class);

    protected FormTemplateVersionDAO(EntityManager em) {
        super(em, FormTemplateVersion.class);
    }

    public Optional<FormTemplateVersion> findByInternalName(String projectName, String internalName) {
        try {
            return Optional.of(em.createNativeQuery("SELECT ?x WHERE { ?x a <http://onto.fel.cvut.cz/ontologies/sformsmanager#FormTemplateVersion> . ?x <http://onto.fel.cvut.cz/ontologies/sformsmanager#internalName> ?internalName . }", FormTemplateVersion.class)
                    .setDescriptor(getDescriptorForProject(projectName))
                    .setParameter("internalName", internalName)
                    .getSingleResult());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
