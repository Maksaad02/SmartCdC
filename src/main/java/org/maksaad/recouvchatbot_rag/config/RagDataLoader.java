package org.maksaad.recouvchatbot_rag.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader; // Changed from PagePdfDocumentReader
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagDataLoader {

    // 1. Point to your new Markdown file
    // Ensure you move the file to src/main/resources/pdfs/ (or rename folder to 'docs')
    @Value("classpath:/pdfs/repportDebtRecoveryLaw.md")
    private Resource markdownResource;

    private final JdbcClient jdbcClient;
    private final VectorStore vectorStore;

    public RagDataLoader(JdbcClient jdbcClient, VectorStore vectorStore) {
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void initStore() {
        // Check if DB is empty
        Integer count = jdbcClient.sql("select count(*) from vector_store")
                .query(Integer.class).single();

        if (count == 0 && markdownResource.exists()) {
            System.out.println("Loading Markdown document into Vector Store...");

            // 2. Use TextReader for Markdown
            // TextReader reads the file as plain text/markdown without complex PDF parsing
            TextReader textReader = new TextReader(markdownResource);
            textReader.getCustomMetadata().put("filename", "repportDebtRecoveryLaw.md");
            List<Document> documents = textReader.get();

            // 3. Configure the Splitter for "Good Chunking"
            // For Legal Markdown, we want larger chunks to keep "Articles" intact.
            // defaultChunkSize: 1000 tokens (~750 words) - Good for capturing full legal articles
            // minChunkSizeChars: 350 - Avoids tiny, useless fragments
            // minChunkLengthToEmbed: 5 - Ignore whitespace
            // keepSeparator: true - Important! Keeps the structural formatting
            TextSplitter textSplitter = new TokenTextSplitter(1000, 350, 5, 10000, true);

            List<Document> chunks = textSplitter.split(documents);

            // 4. Save to Vector Store
            vectorStore.add(chunks);

            System.out.println("Successfully added " + chunks.size() + " chunks.");
        }
    }
}