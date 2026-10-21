import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query-time RAG assistant. Takes a natural-language query, embeds it,
 * pulls the top-k nearest chunks from the {@link RepoIndexer}'s index,
 * formats them into the RAG prompt template, and calls the LLM.
 *
 * Session 6A: complete {@link #retrieve(String, int)} and
 * {@link #buildPrompt(String, List)}.  Do NOT edit {@link #answer}.
 */
public class RagAssistant {

    private final RepoIndexer indexer;
    private final Embedder embedder;
    private final LlmClient llm;
    private final Path promptTemplate;

    public RagAssistant(RepoIndexer indexer, Embedder embedder, LlmClient llm, Path promptTemplate) {
        this.indexer = indexer;
        this.embedder = embedder;
        this.llm = llm;
        this.promptTemplate = promptTemplate;
    }

    public String answer(String query, int k) throws IOException {
        List<Chunk> hits = retrieve(query, k);
        String prompt = buildPrompt(query, hits);
        return llm.complete(prompt);
    }

    /**
     * Embeds the query, ranks every indexed chunk by cosine similarity to
     * it (highest first; ties keep index order), and returns the top k.
     */
    protected List<Chunk> retrieve(String query, int k) {
        if (k <= 0) return new ArrayList<>();
        float[] q = embedder.embed(query);
        List<Chunk> ranked = new ArrayList<>(indexer.getIndex());
        ranked.removeIf(c -> c.embedding() == null);
        Map<Chunk, Double> score = new IdentityHashMap<>();
        for (Chunk c : ranked) score.put(c, cosine(q, c.embedding()));
        ranked.sort(Comparator.comparingDouble((Chunk c) -> score.get(c)).reversed());
        return new ArrayList<>(ranked.subList(0, Math.min(k, ranked.size())));
    }

    /** Full cosine similarity, so it is correct even for non-normalised embedders. */
    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dim mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * Loads the template from {@link #promptTemplate} and substitutes
     * {@code {query}} with the query and {@code {context}} with the hits,
     * each rendered as a {@code source:line} header followed by the chunk
     * content, separated by blank lines. Substitution is a single pass, so
     * placeholder text inside the query or a chunk is left as-is.
     */
    protected String buildPrompt(String query, List<Chunk> hits) throws IOException {
        String template = Files.readString(promptTemplate);

        StringBuilder context = new StringBuilder();
        for (Chunk c : hits) {
            if (context.length() > 0) context.append('\n');
            context.append(c.source()).append(':').append(c.startLine()).append('\n')
                   .append(c.content().stripTrailing()).append('\n');
        }
        if (hits.isEmpty()) context.append("(no chunks retrieved)");

        Map<String, String> values = Map.of(
                "query", query == null ? "" : query,
                "context", context.toString().stripTrailing());
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.get(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(query|context)\\}");
}
