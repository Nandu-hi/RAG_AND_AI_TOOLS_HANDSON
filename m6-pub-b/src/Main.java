import java.io.IOException;
import java.nio.file.Path;

public class Main {

    private static final int STEP_BUDGET = 5;
    private static final int TOKEN_BUDGET = 8000;

    public static void main(String[] args) throws IOException {
        String query = args.length > 0
                ? String.join(" ", args)
                : "Which books mention agents?";
        AgentDriver driver = new AgentDriver(
                new OllamaLlm(), Path.of("prompts/react.txt"), STEP_BUDGET, TOKEN_BUDGET);
        System.out.println(driver.run(query));
    }
}
