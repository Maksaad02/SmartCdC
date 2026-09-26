package org.maksaad.recouvchatbot_rag.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Indexe le document de reference dans la base vectorielle, de facon sure.
 *
 * L'ancienne version (un @PostConstruct qui ne faisait rien si la table n'etait pas vide) avait
 * quatre defauts :
 *  - modifier le document n'avait aucun effet, l'index derivait silencieusement ;
 *  - deux instances demarrant ensemble indexaient toutes deux (doublons) ;
 *  - un echec en cours de route laissait une table partiellement remplie, ensuite
 *    consideree comme "deja indexee" a jamais ;
 *  - si OpenAI etait indisponible au demarrage, l'application ne demarrait pas.
 *
 * Ici : une empreinte du document, du decoupage et du modele d'embedding declenche la
 * reindexation quand l'un d'eux change ; un verrou consultatif PostgreSQL serialise les
 * instances ; tout (suppression des anciens passages, ajout, empreinte) se fait dans une
 * seule transaction, donc soit l'ancienne version reste intacte, soit la nouvelle est
 * complete ; et un echec n'empeche pas le demarrage : il est journalise et retente.
 */
@Component
public class KnowledgeBaseIngestion {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestion.class);

    /** A incrementer quand la facon de decouper le document change : force une reindexation. */
    static final String SPLITTER_VERSION = "md-sections-v1";
    static final String SOURCE = "recouvrement-maroc.md";
    /** Cle du verrou consultatif : une constante arbitraire propre a cette application. */
    private static final long LOCK_KEY = 7_412_005_113L;
    /** ~900 jetons : au-dela, une section est redecoupee. */
    private static final int MAX_SECTION_CHARS = 3500;

    public enum Result { UP_TO_DATE, INDEXED }

    private final Resource document;
    private final JdbcClient jdbc;
    private final VectorStore vectorStore;
    private final TransactionTemplate transaction;
    private final String embeddingModel;
    private final int dimensions;

    private volatile boolean retryPending;

    public KnowledgeBaseIngestion(
            @Value("classpath:/knowledge/recouvrement-maroc.md") Resource document,
            JdbcClient jdbc,
            VectorStore vectorStore,
            PlatformTransactionManager transactionManager,
            @Value("${spring.ai.openai.embedding.options.model:unknown}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}") int dimensions) {
        this.document = document;
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
        this.transaction = new TransactionTemplate(transactionManager);
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
    }

    /** Non bloquant : sans indexation reussie, le chatbot demarre quand meme (ses outils marchent). */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        attempt();
    }

    @Scheduled(
            initialDelayString = "${app.rag.ingestion.retry-ms:600000}",
            fixedDelayString = "${app.rag.ingestion.retry-ms:600000}")
    public void retryIfPending() {
        if (retryPending) {
            attempt();
        }
    }

    private void attempt() {
        try {
            Result result = ingestIfChanged();
            retryPending = false;
            log.info("Base de connaissances : {}", result == Result.INDEXED ? "reindexée" : "à jour");
        } catch (Exception e) {
            retryPending = true;
            log.error("Indexation de la base de connaissances impossible (nouvel essai planifié) : {}",
                    e.getMessage(), e);
        }
    }

    public Result ingestIfChanged() {
        byte[] bytes = readDocument();
        String hash = fingerprint(bytes);
        List<Document> chunks = chunk(new String(bytes, StandardCharsets.UTF_8));
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Le document de référence ne contient aucun passage exploitable");
        }

        return transaction.execute(status -> {
            // Serialise les instances : la seconde attend ici, puis constate que c'est deja fait.
            jdbc.sql("select pg_advisory_xact_lock(?)").param(LOCK_KEY).query((rs, i) -> Boolean.TRUE).single();

            String stored = jdbc.sql("select content_hash from rag_ingestion where source = ?")
                    .param(SOURCE).query(String.class).optional().orElse(null);
            int existing = jdbc.sql("select count(*) from vector_store where metadata ->> 'source' = ?")
                    .param(SOURCE).query(Integer.class).single();
            if (hash.equals(stored) && existing > 0) {
                return Result.UP_TO_DATE;
            }

            jdbc.sql("delete from vector_store where metadata ->> 'source' = ?").param(SOURCE).update();
            vectorStore.add(chunks); // appelle OpenAI ; toute erreur annule la transaction
            jdbc.sql("""
                    insert into rag_ingestion (source, content_hash, chunk_count, ingested_at)
                    values (?, ?, ?, now())
                    on conflict (source) do update
                    set content_hash = excluded.content_hash,
                        chunk_count = excluded.chunk_count,
                        ingested_at = excluded.ingested_at
                    """)
                    .param(SOURCE).param(hash).param(chunks.size()).update();
            return Result.INDEXED;
        });
    }

    List<Document> chunk(String markdown) {
        List<Document> documents = new ArrayList<>();
        TokenTextSplitter overflowSplitter = new TokenTextSplitter(800, 350, 5, 10000, true);
        int index = 0;
        for (MarkdownSectionSplitter.Section section : MarkdownSectionSplitter.split(markdown)) {
            if (section.text().length() <= MAX_SECTION_CHARS) {
                documents.add(new Document(section.text(), metadata(section.path(), index++)));
                continue;
            }
            // Section trop longue : redecoupee, et chaque morceau garde son chemin de titres.
            Document whole = new Document(section.text(), metadata(section.path(), index));
            for (Document piece : overflowSplitter.split(whole)) {
                String text = piece.getText() != null && piece.getText().startsWith(section.path())
                        ? piece.getText()
                        : section.path() + "\n\n" + piece.getText();
                documents.add(new Document(text, metadata(section.path(), index++)));
            }
        }
        return documents;
    }

    private Map<String, Object> metadata(String sectionPath, int index) {
        // scope=public : la recherche est filtree dessus (voir ChatAiService). Le jour ou des
        // documents propres a une organisation seront indexes, ils recevront un autre scope
        // et ne seront jamais servis par defaut a un autre client.
        return Map.of("source", SOURCE, "section", sectionPath, "scope", "public", "chunk", index);
    }

    private byte[] readDocument() {
        try {
            return document.getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Document de référence illisible : " + document, e);
        }
    }

    /** Empreinte du document ET de tout ce qui change ce qui est indexe (decoupage, modele, dimension). */
    private String fingerprint(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            digest.update(("|" + SPLITTER_VERSION + "|" + embeddingModel + "|" + dimensions)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
