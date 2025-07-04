package cz.cvut.kbss.sformsmanager.persistence.dao.local;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.sformsmanager.exception.PersistenceException;
import cz.cvut.kbss.sformsmanager.model.Vocabulary;
import cz.cvut.kbss.sformsmanager.model.persisted.local.Record;
import cz.cvut.kbss.sformsmanager.persistence.dao.LocalEntityBaseDAO;
import org.slf4j.Logger;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;

@Repository
public class RecordDAO extends LocalEntityBaseDAO<Record> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RecordDAO.class);

    protected RecordDAO(EntityManager em) {
        super(em, Record.class);
    }

    public List<Record> findAllWithRecordVersion(String projectDescriptorName) {
        try {
            return em.createNativeQuery("SELECT DISTINCT ?x WHERE { ?x a ?type . ?x ?hasRecordVersions ?v . }", type)
                    .setDescriptor(getDescriptorForProject(projectDescriptorName))
                    .setParameter("type", typeUri)
                    .setParameter("hasRecordVersions", URI.create(Vocabulary.p_hasRecordVersions))
                    .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
