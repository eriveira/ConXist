package com.conxist.capture.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.capture.adapter.secrets.SecretBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * On start, encrypts captured text written before encryption at rest existed, a batch at a time, so no plaintext
 * stays in the database. Rows are only touched if they still hold plaintext, so running it twice is harmless.
 */
@Component
class SealPlaintextDocuments implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SealPlaintextDocuments.class);
    private static final int BATCH = 100;

    private final JdbcClient jdbc;
    private final SecretBox box;
    private final TransactionTemplate transactions;

    SealPlaintextDocuments(JdbcClient jdbc, SecretBox box, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.box = box;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int sealed = 0;
        int batch;
        do {
            batch = transactions.execute(status -> sealBatch());
            sealed += batch;
        } while (batch == BATCH);
        if (sealed > 0) {
            log.info("Encrypted the text of {} documents captured before encryption at rest", sealed);
        }
    }

    private int sealBatch() {
        List<Plain> rows = jdbc.sql("""
                select id, text_content from captured_documents
                where text_content is not null
                limit :batch
                for update skip locked
                """)
                .param("batch", BATCH)
                .query((row, n) -> new Plain(row.getObject("id", UUID.class), row.getString("text_content")))
                .list();
        for (Plain row : rows) {
            jdbc.sql("update captured_documents set text_sealed = :sealed, text_content = null where id = :id")
                    .param("sealed", JdbcCapturedDocumentRepository.seal(box, row.id(), row.text()))
                    .param("id", row.id())
                    .update();
        }
        return rows.size();
    }

    private record Plain(UUID id, String text) {
    }
}
