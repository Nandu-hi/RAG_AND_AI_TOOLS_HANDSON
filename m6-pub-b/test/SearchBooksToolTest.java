import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchBooksToolTest {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private final Tool tool = new SearchBooksTool();

    private static List<String> ids(String json) {
        List<String> ids = new ArrayList<>();
        Matcher m = ID.matcher(json);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    @Test
    public void name_isSearchBooks() {
        assertEquals("search_books", tool.name());
    }

    @Test
    public void authorAndYearRange_filtersBoth() {
        assertEquals(List.of("b002"), ids(tool.call("Ursula K. Le Guin|1970|1979")));
    }

    @Test
    public void authorOnly_returnsAllByThatAuthor() {
        assertEquals(List.of("b001", "b002", "b003"), ids(tool.call("Ursula K. Le Guin||")));
    }

    @Test
    public void authorMatch_isCaseInsensitiveSubstring() {
        assertEquals(List.of("b012", "b013"), ids(tool.call("bradbury||")));
        assertEquals(List.of("b001", "b002", "b003"), ids(tool.call("LE GUIN||")));
    }

    @Test
    public void yearRangeOnly_spansAllAuthors() {
        assertEquals(List.of("b008", "b016", "b017", "b024", "b027"),
                ids(tool.call("|1990|2000")));
    }

    @Test
    public void yearRange_isInclusiveAtBothEnds() {
        assertEquals(List.of("b012", "b013"), ids(tool.call("Ray Bradbury|1950|1953")));
        assertEquals(List.of("b013"), ids(tool.call("Ray Bradbury|1950|1950")));
    }

    @Test
    public void openEndedRanges_applyOneBound() {
        assertEquals(List.of("b011"), ids(tool.call("||1932")));
        assertEquals(List.of("b030"), ids(tool.call("|2016|")));
    }

    @Test
    public void emptyArgument_returnsWholeCatalogue() {
        assertEquals(30, ids(tool.call("||")).size());
        assertEquals(30, ids(tool.call("")).size());
        assertEquals(ids(new ListBooksTool().call("")), ids(tool.call("||")));
    }

    @Test
    public void missingParts_areTreatedAsUnconstrained() {
        assertEquals(List.of("b012", "b013"), ids(tool.call("Ray Bradbury")));
        assertEquals(List.of("b012"), ids(tool.call("Ray Bradbury|1951")));
    }

    @Test
    public void surroundingWhitespace_isIgnored() {
        assertEquals(List.of("b012", "b013"), ids(tool.call("  Ray Bradbury | 1950 | 1953 ")));
    }

    @Test
    public void noMatches_returnsEmptyJsonArray() {
        assertEquals("[]", tool.call("Nobody At All||"));
        assertEquals("[]", tool.call("Ray Bradbury|2000|2010"));
        assertEquals("[]", tool.call("|1979|1970"));
    }

    @Test
    public void nonNumericYear_returnsError() {
        assertTrue(tool.call("Ray Bradbury|abc|").startsWith("ERROR"));
        assertTrue(tool.call("||19x0").startsWith("ERROR"));
    }

    @Test
    public void result_isJsonArrayOfFullEntries() {
        String out = tool.call("Frank Herbert||");
        assertTrue(out.startsWith("["), out);
        assertTrue(out.endsWith("]"), out);
        assertTrue(out.contains("\"title\": \"Dune\""), out);
        assertTrue(out.contains("\"genre\": \"science fiction\""), out);
        assertFalse(out.contains("},\n]"), "no trailing comma: " + out);
    }
}
