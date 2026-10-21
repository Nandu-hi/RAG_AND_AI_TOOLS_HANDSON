import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * search_books: targeted lookup by author and year range.
 *
 * Expected argument format: {@code author|year_from|year_to}
 * where empty parts mean "no constraint".  Example:
 * <pre>
 *   search_books("Ursula K. Le Guin|1970|1979")
 * </pre>
 * Any part may be left empty, e.g. {@code "|1990|2000"} for a
 * year-range query across all authors.
 *
 * Author matching is a case-insensitive substring match; the year
 * range is inclusive.  books.json holds one flat object per line, so
 * entries are filtered line by line without a JSON library.
 */
public class SearchBooksTool implements Tool {

    private static final Path BOOKS = Path.of("books.json");
    private static final Pattern AUTHOR = Pattern.compile("\"author\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern YEAR = Pattern.compile("\"year\"\\s*:\\s*(-?\\d+)");

    @Override
    public String name() {
        return "search_books";
    }

    @Override
    public String call(String arg) {
        String[] parts = (arg == null ? "" : arg).split("\\|", -1);
        String author = parts.length > 0 ? parts[0].trim().toLowerCase() : "";
        Integer from;
        Integer to;
        try {
            from = parseYear(parts, 1);
            to = parseYear(parts, 2);
        } catch (NumberFormatException e) {
            return "ERROR: expected 'author|year_from|year_to', got '" + arg + "'";
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(BOOKS);
        } catch (IOException e) {
            return "ERROR: could not read " + BOOKS + ": " + e.getMessage();
        }

        List<String> matches = new ArrayList<>();
        for (String line : lines) {
            String entry = line.trim();
            if (!entry.startsWith("{")) {
                continue;
            }
            if (entry.endsWith(",")) {
                entry = entry.substring(0, entry.length() - 1);
            }
            Matcher a = AUTHOR.matcher(entry);
            Matcher y = YEAR.matcher(entry);
            if (!a.find() || !y.find()) {
                continue;
            }
            int year = Integer.parseInt(y.group(1));
            if (!author.isEmpty() && !a.group(1).toLowerCase().contains(author)) {
                continue;
            }
            if ((from != null && year < from) || (to != null && year > to)) {
                continue;
            }
            matches.add(entry);
        }

        if (matches.isEmpty()) {
            return "[]";
        }
        return "[\n  " + String.join(",\n  ", matches) + "\n]";
    }

    private static Integer parseYear(String[] parts, int i) {
        if (parts.length <= i || parts[i].isBlank()) {
            return null;
        }
        return Integer.parseInt(parts[i].trim());
    }
}
