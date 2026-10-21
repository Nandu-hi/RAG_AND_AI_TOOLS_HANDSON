import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks the three chunkFile requirements on every method chunk:
 * file-path prefix, enclosing class signature, and leading JavaDoc.
 */
public class ChunkFileTest {

    private static final String SRC = String.join("\n",
        "package demo;",
        "",
        "import java.util.List;",
        "",
        "/** Class doc, must not leak into method chunks. */",
        "@SuppressWarnings({\"unchecked\", \"rawtypes\"})",
        "public final class Shelf<T extends Comparable<T>>",
        "        extends Base<T>",
        "        implements Runnable, java.io.Serializable {",
        "",
        "    private int count;",
        "",
        "    /**",
        "     * Builds an empty shelf.",
        "     * @param n capacity",
        "     */",
        "    public Shelf(int n) { count = n; }",
        "",
        "    /** Runs it. */",
        "    @Override",
        "    public void run() {",
        "        Runnable r = new Runnable() { public void run() {} };",
        "    }",
        "",
        "    int plain() { return count; }",
        "",
        "    /** Orphaned by the line comment below. */",
        "    // helper",
        "    int orphan() { return 1; }",
        "",
        "    int x; /** Same-line doc. */ int sameLine() { return x; }",
        "",
        "    static class Inner implements Cloneable {",
        "        /** Inner doc. */",
        "        void innerMethod() {}",
        "    }",
        "}",
        "",
        "record Point(int x, int y) {",
        "    /** Sum. */",
        "    int sum() { return x + y; }",
        "}",
        "",
        "enum Color implements Runnable {",
        "    RED, GREEN;",
        "    public void run() {}",
        "}",
        "");

    private List<Chunk> chunks(Path dir) throws IOException {
        Path f = dir.resolve("Shelf.java");
        Files.writeString(f, SRC);
        return new RepoIndexer(new StubEmbedder()).chunkFile(f);
    }

    private Chunk chunkFor(List<Chunk> all, String methodHeader) {
        return all.stream().filter(c -> c.content().contains(methodHeader)).findFirst()
                  .orElseThrow(() -> new AssertionError("no chunk contains: " + methodHeader));
    }

    private static final String SHELF_SIG =
        "@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n"
      + "public final class Shelf<T extends Comparable<T>>\n"
      + "        extends Base<T>\n"
      + "        implements Runnable, java.io.Serializable {";

    @Test
    public void oneChunkPerTopLevelMethodOrConstructor(@TempDir Path dir) throws IOException {
        // Shelf(), run(), plain(), orphan(), sameLine(), innerMethod(), sum(), Color.run()
        // -- the anonymous Runnable.run() stays inside run()'s chunk.
        assertEquals(8, chunks(dir).size());
    }

    @Test
    public void everyChunkStartsWithFilePath(@TempDir Path dir) throws IOException {
        for (Chunk c : chunks(dir)) {
            assertTrue(c.content().startsWith("// " + dir.resolve("Shelf.java") + "\n"), c.content());
        }
    }

    @Test
    public void enclosingClassSignatureIsVerbatim(@TempDir Path dir) throws IOException {
        List<Chunk> all = chunks(dir);
        for (String m : List.of("public Shelf(int n)", "public void run() {\n        Runnable",
                                "int plain()", "int orphan()", "int sameLine()")) {
            String text = chunkFor(all, m).content();
            assertTrue(text.contains(SHELF_SIG), "missing Shelf signature in:\n" + text);
            assertFalse(text.contains("Class doc"), "class JavaDoc leaked into:\n" + text);
        }
        String inner = chunkFor(all, "void innerMethod()").content();
        assertTrue(inner.contains(SHELF_SIG), inner);
        assertTrue(inner.contains("static class Inner implements Cloneable {"), inner);
        assertTrue(chunkFor(all, "int sum()").content().contains("record Point(int x, int y) {"));
        assertTrue(chunkFor(all, "enum Color").content().contains(
            "enum Color implements Runnable {\n    public void run() {}\n}"));
    }

    @Test
    public void leadingJavaDocIsPreservedVerbatim(@TempDir Path dir) throws IOException {
        List<Chunk> all = chunks(dir);
        assertTrue(chunkFor(all, "public Shelf(int n)").content().contains(
            "    /**\n     * Builds an empty shelf.\n     * @param n capacity\n     */\n    public Shelf(int n)"));
        assertTrue(chunkFor(all, "@Override").content().contains("    /** Runs it. */\n    @Override\n    public void run()"));
        assertTrue(chunkFor(all, "int orphan()").content().contains(
            "/** Orphaned by the line comment below. */\n    // helper\n    int orphan()"));
        assertTrue(chunkFor(all, "void innerMethod()").content().contains("/** Inner doc. */"));
        assertTrue(chunkFor(all, "int sum()").content().contains("/** Sum. */"));
    }

    @Test
    public void chunkHoldsOnlyItsOwnMember(@TempDir Path dir) throws IOException {
        List<Chunk> all = chunks(dir);
        String same = chunkFor(all, "int sameLine()").content();
        assertTrue(same.contains("/** Same-line doc. */ int sameLine()"), same);
        assertFalse(same.contains("int x;"), "previous field leaked into:\n" + same);
        assertFalse(chunkFor(all, "int plain()").content().contains("/**"), "plain() has no JavaDoc");
    }

    @Test
    public void sampleRepoChunksAllMeetTheContract() throws IOException {
        RepoIndexer idx = new RepoIndexer(new StubEmbedder());
        idx.indexDirectory(Path.of("sample-repo"));
        for (Chunk c : idx.getIndex()) {
            String[] lines = c.content().split("\n");
            assertEquals("// " + c.source(), lines[0]);
            assertTrue(lines[1].matches(".*\\b(class|interface|enum|record)\\b.*\\{$")
                       || lines[1].startsWith("@"), "no type signature after path:\n" + c.content());
        }
    }
}
