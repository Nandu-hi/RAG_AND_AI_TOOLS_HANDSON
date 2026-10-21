import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * list_books: returns the entire book catalogue, verbatim.
 *
 * Intentionally greedy -- the whole JSON file goes back as the tool's
 * reply.  Session 6B asks the agent to prefer the cheaper
 * {@link SearchBooksTool} when the query is specific enough.
 */
public class ListBooksTool implements Tool {

    private static final Path BOOKS = Path.of("books.json");

    @Override
    public String name() {
        return "list_books";
    }

    @Override
    public String call(String arg) {
        try {
            return Files.readString(BOOKS);
        } catch (IOException e) {
            return "ERROR: could not read " + BOOKS + ": " + e.getMessage();
        }
    }
}
