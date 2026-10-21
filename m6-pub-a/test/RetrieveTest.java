import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RetrieveTest {

    private RagAssistant assistant(RepoIndexer idx, Embedder emb) {
        return new RagAssistant(idx, emb, new StubLlmClient(), Path.of("prompts/rag.txt"));
    }

    @Test
    public void exactChunkTextRanksFirst() throws IOException {
        StubEmbedder emb = new StubEmbedder();
        RepoIndexer idx = new RepoIndexer(emb);
        idx.indexDirectory(Path.of("sample-repo"));
        // Pick a chunk from the middle so "first k in index order" would fail.
        Chunk target = idx.getIndex().get(idx.size() / 2);

        List<Chunk> hits = assistant(idx, emb).retrieve(target.content(), 3);
        assertEquals(3, hits.size());
        assertSame(target, hits.get(0));
    }

    @Test
    public void kIsClampedToIndexSize() throws IOException {
        StubEmbedder emb = new StubEmbedder();
        RepoIndexer idx = new RepoIndexer(emb);
        idx.indexDirectory(Path.of("sample-repo"));
        RagAssistant ra = assistant(idx, emb);
        assertEquals(idx.size(), ra.retrieve("anything", idx.size() + 100).size());
        assertTrue(ra.retrieve("anything", 0).isEmpty());
    }
}
