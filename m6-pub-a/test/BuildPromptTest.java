import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BuildPromptTest {

    private final RagAssistant ra = new RagAssistant(
            new RepoIndexer(new StubEmbedder()), new StubEmbedder(),
            new StubLlmClient(), Path.of("prompts/rag.txt"));

    private final Chunk a = new Chunk("src/A.java", 12, 15, "// src/A.java\nclass A {\n    void f() {}\n}\n", null);
    private final Chunk b = new Chunk("src/B.java", 3, 4, "// src/B.java\nclass B {\n    int g() { return 1; }\n}\n", null);

    @Test
    public void substitutesBothPlaceholders() throws IOException {
        String p = ra.buildPrompt("Where is f?", List.of(a, b));
        assertFalse(p.contains("{query}"), p);
        assertFalse(p.contains("{context}"), p);
        assertTrue(p.contains("Question:\nWhere is f?\n"), p);
        assertTrue(p.startsWith("You are a code-search assistant"), p);
        assertTrue(p.strip().endsWith("Answer:"), p);
    }

    @Test
    public void contextIsSourceLineThenContentInRankOrder() throws IOException {
        String p = ra.buildPrompt("q", List.of(a, b));
        String expected =
              "src/A.java:12\n// src/A.java\nclass A {\n    void f() {}\n}\n"
            + "\n"
            + "src/B.java:3\n// src/B.java\nclass B {\n    int g() { return 1; }\n}";
        assertTrue(p.contains("Context (top-K retrieved chunks):\n" + expected + "\n"), p);
    }

    @Test
    public void placeholderTextInInputsIsNotReExpanded() throws IOException {
        Chunk weird = new Chunk("src/W.java", 1, 1, "String s = \"{query} $1 \\\\\";", null);
        String p = ra.buildPrompt("what is {context}?", List.of(weird));
        assertTrue(p.contains("Question:\nwhat is {context}?\n"), p);
        assertTrue(p.contains("String s = \"{query} $1 \\\\\";"), p);
    }

    @Test
    public void emptyHitsSaySoExplicitly() throws IOException {
        assertTrue(ra.buildPrompt("q", List.of()).contains("(no chunks retrieved)"));
    }
}
