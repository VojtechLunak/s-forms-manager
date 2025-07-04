package cz.cvut.kbss.sformsmanager.persistence.dao.local;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.sformsmanager.model.persisted.local.FormTemplate;
import cz.cvut.kbss.sformsmanager.persistence.dao.LocalEntityBaseDAO;
import org.slf4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
public class FormTemplateDAO extends LocalEntityBaseDAO<FormTemplate> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(FormTemplateDAO.class);

    protected FormTemplateDAO(EntityManager em) {
        super(em, FormTemplate.class);
    }
}
