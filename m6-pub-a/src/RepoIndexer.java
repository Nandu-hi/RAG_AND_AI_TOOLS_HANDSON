import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Walks a directory of .java files, splits each file into method-level
 * chunks, embeds each chunk, and stores the result in an in-memory index.
 *
 * Session 6A: complete {@link #chunkFile(Path)} and {@link #embed(String)}.
 */
public class RepoIndexer {

    private final Embedder embedder;
    private final List<Chunk> index = new ArrayList<>();

    public RepoIndexer(Embedder embedder) {
        this.embedder = embedder;
    }

    /** Walks dir, indexing every *.java file below it. */
    public void indexDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(this::indexFile);
        }
    }

    private void indexFile(Path file) {
        try {
            List<Chunk> chunks = chunkFile(file);
            for (Chunk c : chunks) {
                float[] v = embed(c.content());
                index.add(c.withEmbedding(v));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to index " + file, e);
        }
    }

    /**
     * Splits the file into one chunk per method or constructor, parsed with
     * JavaParser. Each chunk's text is, in order:
     * <ol>
     *   <li>{@code // <file path>} on the first line;</li>
     *   <li>the verbatim signature of every enclosing type, outermost first
     *       (annotations, modifiers, generics, extends/implements -- no body,
     *       no type JavaDoc);</li>
     *   <li>the member's verbatim source, starting at its leading JavaDoc
     *       if it has one;</li>
     *   <li>closing braces for the enclosing types.</li>
     * </ol>
     * Methods of anonymous/local classes stay inside their enclosing method's
     * chunk. Falls back to a single whole-file chunk if the file does not
     * parse or declares no methods.
     */
    protected List<Chunk> chunkFile(Path file) throws IOException {
        String body = Files.readString(file);
        List<Chunk> whole = List.of(new Chunk(file.toString(), 1, lineCount(body), body, null));

        ParseResult<CompilationUnit> parsed = new JavaParser(
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17))
                .parse(body);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            return whole;
        }

        Source src = new Source(body);
        List<Chunk> chunks = new ArrayList<>();
        for (CallableDeclaration<?> m : parsed.getResult().get().findAll(CallableDeclaration.class)) {
            if (m.findAncestor(CallableDeclaration.class).isPresent()) continue;
            Optional<TypeDeclaration> owner = m.findAncestor(TypeDeclaration.class);
            if (owner.isEmpty() || m.getRange().isEmpty()) continue;

            Position start = leadingJavadoc(m, src).orElse(m.getRange().get().begin);
            Position end = m.getRange().get().end;

            // Enclosing types, outermost first.
            List<TypeDeclaration<?>> types = new ArrayList<>();
            for (Optional<TypeDeclaration> t = owner; t.isPresent(); t = t.get().findAncestor(TypeDeclaration.class)) {
                types.add(0, t.get());
            }

            StringBuilder text = new StringBuilder("// ").append(file).append('\n');
            for (TypeDeclaration<?> t : types) {
                text.append(src.indentOf(t.getBegin().get())).append(typeSignature(t, src)).append('\n');
            }
            text.append(src.indentOf(start)).append(src.slice(start, end)).append('\n');
            for (int i = types.size() - 1; i >= 0; i--) {
                text.append(src.indentOf(types.get(i).getBegin().get())).append("}\n");
            }
            chunks.add(new Chunk(file.toString(), start.line, end.line, text.toString(), null));
        }
        return chunks.isEmpty() ? whole : chunks;
    }

    /**
     * Start of the JavaDoc that leads {@code m}: the attached one, or an
     * orphaned one separated from the member only by whitespace and
     * non-JavaDoc comments (JavaParser attaches the nearest comment, so
     * {@code /** doc *&#47; // note} leaves the JavaDoc orphaned).
     */
    private static Optional<Position> leadingJavadoc(CallableDeclaration<?> m, Source src) {
        Optional<Position> attached = m.getJavadocComment().flatMap(Node::getBegin);
        if (attached.isPresent()) return attached;

        Position from = m.getComment().flatMap(Node::getBegin).orElse(m.getBegin().get());
        return m.getParentNode().stream()
                .flatMap(p -> p.getOrphanComments().stream())
                .filter(c -> c instanceof JavadocComment && c.getRange().isPresent())
                .filter(c -> c.getEnd().get().isBefore(from)
                          && src.between(c.getEnd().get(), from).isBlank())
                .map(c -> c.getBegin().get())
                .findFirst();
    }

    /**
     * The type's declaration as written in the source, from its first
     * annotation/modifier through the brace that opens its body.
     */
    private static String typeSignature(TypeDeclaration<?> type, Source src) {
        // First '{' after the name that is not inside parentheses (record
        // components and annotation arguments may contain braces).
        JavaToken tok = type.getName().getTokenRange().get().getEnd();
        int parens = 0;
        while (true) {
            tok = tok.getNextToken().get();
            String t = tok.getText();
            if (t.equals("(")) parens++;
            else if (t.equals(")")) parens--;
            else if (t.equals("{") && parens == 0) break;
        }
        return src.slice(type.getBegin().get(), tok.getRange().get().begin);
    }

    private static int lineCount(String s) {
        return (int) s.lines().count();
    }

    /** Original file text, addressed by JavaParser's 1-based line/column positions. */
    private static final class Source {
        private final String text;
        private final int[] lineStarts;

        Source(String text) {
            this.text = text;
            List<Integer> starts = new ArrayList<>(List.of(0));
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') starts.add(i + 1);
            }
            this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        }

        int offset(Position p) {
            return lineStarts[p.line - 1] + p.column - 1;
        }

        /** Text from {@code a} through {@code b}, both inclusive. */
        String slice(Position a, Position b) {
            return text.substring(offset(a), offset(b) + 1);
        }

        /** Text strictly between {@code a} and {@code b}. */
        String between(Position a, Position b) {
            return text.substring(offset(a) + 1, offset(b));
        }

        /** Leading whitespace of the line containing {@code p}. */
        String indentOf(Position p) {
            int s = lineStarts[p.line - 1], i = s;
            while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
            return text.substring(s, i);
        }
    }

    /**
     * TODO(session-6A): swap the stub for a real embedder implementation
     * (langchain4j all-MiniLM-L6-v2 is one option). The signature stays
     * the same; only the interface binding at construction time changes.
     */
    protected float[] embed(String text) {
        return embedder.embed(text);
    }

    public List<Chunk> getIndex() {
        return index;
    }

    public int size() {
        return index.size();
    }
}
