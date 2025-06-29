package cz.cvut.kbss.sformsmanager.persistence.dao.local;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.sformsmanager.model.persisted.local.SubmittedAnswer;
import cz.cvut.kbss.sformsmanager.persistence.dao.LocalEntityBaseDAO;
import org.slf4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
public class SubmittedAnswerDAO extends LocalEntityBaseDAO<SubmittedAnswer> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SubmittedAnswerDAO.class);

    protected SubmittedAnswerDAO(EntityManager em) {
        super(em, SubmittedAnswer.class);
    }
}
