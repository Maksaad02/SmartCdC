package org.maksaad.recouvchatbot_rag.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Migrations et indexation sur une VRAIE base pgvector (Docker), avec un modele d'embedding
 * factice deterministe : rien n'appelle OpenAI, mais tout le reste est reel (Flyway, HNSW,
 * transactions, verrou consultatif).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key-not-used",
        "recouv.backend.api-key=test-only-service-key",
        "recouv.frontend.origin=https://front.test"})
@Import(KnowledgeBaseIngestionTest.FakeEmbeddingConfig.class)
class KnowledgeBaseIngestionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // Cle publique RSA jetable : ce test n'authentifie personne mais le filtre JWT en exige une valide.
        registry.add("app.jwt.public-key", () -> {
            try {
                var generator = java.security.KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                return java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private KnowledgeBaseIngestion ingestion;
    @Autowired private JdbcClient jdbc;
    @Autowired private VectorStore vectorStore;
    @Autowired private PlatformTransactionManager transactionManager;

    /** Chaque test part de l'etat "document d'origine indexe", quel qu'ait ete le test precedent. */
    @BeforeEach
    void restaurer() {
        ingestion.ingestIfChanged();
    }

    // ------------------------------------------------------------ schema

    @Test
    void lesMigrationsCreentLesExtensionsLaTableEtLIndexHnsw() {
        assertEquals(1, jdbc.sql("select count(*) from pg_extension where extname = 'vector'")
                .query(Integer.class).single());

        String indexDef = jdbc.sql("select indexdef from pg_indexes where indexname = 'spring_ai_vector_index'")
                .query(String.class).single();
        assertTrue(indexDef.toLowerCase().contains("hnsw"), indexDef);
        assertTrue(indexDef.contains("vector_cosine_ops"), indexDef);

        assertEquals(1, jdbc.sql("select count(*) from flyway_schema_history where version = '1' and success")
                .query(Integer.class).single(), "V1 doit etre appliquee par Flyway");
    }

    @Test
    void laDimensionDeLaColonneEst1536() {
        // atttypmod = dimension pour le type vector
        Integer dimension = jdbc.sql("""
                select atttypmod from pg_attribute
                where attrelid = 'vector_store'::regclass and attname = 'embedding'
                """).query(Integer.class).single();
        assertEquals(1536, dimension);
    }

    // ------------------------------------------------------------ indexation

    @Test
    void lesPassagesPortentLeurSourceLeurSectionEtLeScopePublic() {
        int total = count();
        assertTrue(total > 0, "aucun passage indexe");
        assertEquals(total, jdbc.sql("""
                select count(*) from vector_store
                where metadata ->> 'source' = ? and metadata ->> 'scope' = 'public'
                  and coalesce(metadata ->> 'section', '') <> ''
                """).param(KnowledgeBaseIngestion.SOURCE).query(Integer.class).single());

        String hash = jdbc.sql("select content_hash from rag_ingestion where source = ?")
                .param(KnowledgeBaseIngestion.SOURCE).query(String.class).single();
        assertEquals(64, hash.length());
    }

    @Test
    void uneSecondeIndexationNeCreeAucunDoublon() {
        int avant = count();

        var resultat = ingestion.ingestIfChanged();

        assertEquals(KnowledgeBaseIngestion.Result.UP_TO_DATE, resultat);
        assertEquals(avant, count());
    }

    @Test
    void modifierLeDocumentRemplaceLesAnciensPassagesAuLieuDeLesAccumuler() {
        KnowledgeBaseIngestion modifiee = avecDocument("""
                # Nouveau texte

                ## Section unique

                Ce document remplace entierement le precedent et ne contient qu'un seul passage exploitable.
                """);

        assertEquals(KnowledgeBaseIngestion.Result.INDEXED, modifiee.ingestIfChanged());

        assertEquals(1, count(), "les passages de l'ancienne version doivent avoir ete supprimes");
        assertEquals(0, jdbc.sql("select count(*) from vector_store where content like '%injonction%'")
                .query(Integer.class).single());
    }

    @Test
    void unEchecPendantLIndexationLaisseLaVersionPrecedenteIntacte() {
        int avant = count();
        String hashAvant = hash();
        VectorStore enPanne = mock(VectorStore.class);
        doThrow(new IllegalStateException("OpenAI indisponible")).when(enPanne).add(anyList());
        KnowledgeBaseIngestion cassee = new KnowledgeBaseIngestion(
                document("# T\n\n## S\n\nUn autre texte suffisamment long pour former un passage complet."),
                jdbc, enPanne, transactionManager, "test-model", 1536);

        assertThrows(IllegalStateException.class, cassee::ingestIfChanged);

        assertEquals(avant, count(), "l'echec ne doit pas avoir supprime les anciens passages");
        assertEquals(hashAvant, hash(), "l'empreinte ne doit pas avoir change : la version precedente reste la reference");
    }

    @Test
    void deuxInstancesQuiIndexentEnMemeTempsNeCreentPasDeDoublons() {
        String texte = "# Doc\n\n## A\n\nPremier passage suffisamment long pour etre retenu par le decoupage.\n\n## B\n\nSecond passage suffisamment long pour etre retenu par le decoupage.";
        KnowledgeBaseIngestion i1 = avecDocument(texte);
        KnowledgeBaseIngestion i2 = avecDocument(texte);

        CompletableFuture.allOf(
                CompletableFuture.runAsync(i1::ingestIfChanged),
                CompletableFuture.runAsync(i2::ingestIfChanged)).join();

        assertEquals(2, count(), "le verrou consultatif doit serialiser les deux instances");
    }

    // ------------------------------------------------------------ recherche

    @Test
    void laRechercheFiltreSurLeScopePublicEtRenvoieDesPassagesAvecLeurSection() {
        var results = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query("injonction de payer").topK(3).similarityThresholdAll()
                        .filterExpression("scope == 'public'").build());

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(d -> "public".equals(d.getMetadata().get("scope"))));
    }

    @Test
    void unPassageHorsScopePublicNEstJamaisRenvoye() {
        // Un document propre a une organisation, indexe avec un autre scope.
        vectorStore.add(List.of(new Document("Donnee confidentielle de l'organisation 42 : injonction de payer",
                java.util.Map.of("source", "org-42.md", "scope", "org-42"))));
        try {
            var results = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("injonction de payer").topK(20).similarityThresholdAll()
                            .filterExpression("scope == 'public'").build());

            assertTrue(results.stream().noneMatch(d -> d.getText().contains("organisation 42")),
                    "un document d'une autre portee ne doit jamais etre servi par la recherche publique");
        } finally {
            jdbc.sql("delete from vector_store where metadata ->> 'source' = 'org-42.md'").update();
        }
    }

    // ------------------------------------------------------------ aides

    private int count() {
        return jdbc.sql("select count(*) from vector_store where metadata ->> 'source' = ?")
                .param(KnowledgeBaseIngestion.SOURCE).query(Integer.class).single();
    }

    private String hash() {
        return jdbc.sql("select content_hash from rag_ingestion where source = ?")
                .param(KnowledgeBaseIngestion.SOURCE).query(String.class).single();
    }

    private ByteArrayResource document(String markdown) {
        return new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8));
    }

    private KnowledgeBaseIngestion avecDocument(String markdown) {
        return new KnowledgeBaseIngestion(document(markdown), jdbc, vectorStore, transactionManager, "test-model", 1536);
    }

    /** Embeddings deterministes (aucun appel reseau) : meme texte -> meme vecteur. */
    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    List<Embedding> embeddings = new ArrayList<>();
                    int i = 0;
                    for (String text : request.getInstructions()) {
                        embeddings.add(new Embedding(vector(text), i++));
                    }
                    return new EmbeddingResponse(embeddings);
                }

                @Override
                public float[] embed(Document document) {
                    return vector(document.getText());
                }

                @Override
                public int dimensions() {
                    return 1536;
                }

                private float[] vector(String text) {
                    Random random = new Random(text == null ? 0 : text.hashCode());
                    float[] v = new float[1536];
                    double norm = 0;
                    for (int i = 0; i < v.length; i++) {
                        v[i] = random.nextFloat() - 0.5f;
                        norm += v[i] * v[i];
                    }
                    float scale = (float) (1.0 / Math.sqrt(norm));
                    for (int i = 0; i < v.length; i++) {
                        v[i] *= scale;
                    }
                    return v;
                }
            };
        }
    }
}
