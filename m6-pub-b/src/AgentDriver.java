import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal ReAct-style agent loop.
 *
 * Each turn we send the LLM the running scratchpad ("Thought/Action/
 * Observation" transcript so far) plus the user's original query, parse
 * exactly one Action out of the LLM's response, call the corresponding
 * tool, append the Observation, and loop.
 *
 * Session 6B TODOs:
 *  - {@link #shouldStop(String, int)}: decide when we're done
 *    (either a "Final Answer:" line has appeared or we hit the step budget).
 *  - {@link #parseAction(String)}: extract the Action name and argument
 *    from the model's most recent response.
 */
public class AgentDriver {

    private final LlmClient llm;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path promptTemplate;
    private final int stepBudget;
    private final int tokenBudget;

    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget) {
        this(llm, promptTemplate, stepBudget, Integer.MAX_VALUE);
    }

    /**
     * @param tokenBudget max tokens (prompt + response, summed over all
     *                    LLM calls) the loop may spend before it stops.
     */
    public AgentDriver(LlmClient llm, Path promptTemplate, int stepBudget,
                       int tokenBudget) {
        this.llm = llm;
        this.promptTemplate = promptTemplate;
        this.stepBudget = stepBudget;
        this.tokenBudget = tokenBudget;
        registerTool(new ListBooksTool());
        registerTool(new SearchBooksTool());
    }

    public void registerTool(Tool t) {
        tools.put(t.name(), t);
    }

    public Map<String, Tool> getTools() {
        return tools;
    }

    /**
     * Run the loop. Returns the final scratchpad (transcript of all
     * thoughts, actions, observations, plus the last LLM response).
     */
    public String run(String userQuery) throws IOException {
        String template = Files.readString(promptTemplate);
        StringBuilder scratch = new StringBuilder();
        String lastResponse = "";
        int iterations = 0;
        int tokensUsed = 0;
        System.out.println("=== Trace: " + userQuery + " ===");
        for (int step = 0; step < stepBudget; step++) {
            String prompt = template
                    .replace("{query}", userQuery)
                    .replace("{scratchpad}", scratch.toString());
            int promptTokens = estimateTokens(prompt);
            if (tokensUsed + promptTokens > tokenBudget) {
                scratch.append("Observation: (token budget exhausted; stopping)\n");
                System.out.println("[stop] token budget exhausted before iteration "
                        + (iterations + 1) + " (" + tokensUsed + " + "
                        + promptTokens + " > " + tokenBudget + ")");
                break;
            }
            iterations++;
            lastResponse = llm.complete(prompt);
            tokensUsed += promptTokens + estimateTokens(lastResponse);
            scratch.append(lastResponse).append('\n');
            System.out.println("--- Iteration " + iterations + " ---");
            System.out.println(lastResponse.strip());
            System.out.println("[tokens] " + tokensUsed + " / " + tokenBudget);
            if (shouldStop(lastResponse, step)) {
                System.out.println("[stop] "
                        + (FINAL_ANSWER.matcher(lastResponse).find()
                                ? "Final Answer reached"
                                : "step budget exhausted"));
                break;
            }
            Map.Entry<String, String> action = parseAction(lastResponse);
            if (action == null) {
                scratch.append("Observation: (no action parsed; stopping)\n");
                System.out.println("[stop] no action parsed");
                break;
            }
            Tool t = tools.get(action.getKey());
            String obs = (t == null)
                    ? "ERROR: unknown tool " + action.getKey()
                    : t.call(action.getValue());
            scratch.append("Observation: ").append(obs).append('\n');
            System.out.println("[tool] " + action.getKey()
                    + "(\"" + action.getValue() + "\")");
            System.out.println("[observation] " + obs);
        }
        System.out.println("=== Loop took " + iterations + " iteration(s), ~"
                + tokensUsed + " tokens ===");
        return scratch.toString();
    }

    /**
     * Rough token count: ~4 characters per token, the usual rule of thumb
     * for English text. LlmClient only returns a String, so we can't get
     * exact usage from the model.
     */
    protected int estimateTokens(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }

    private static final Pattern FINAL_ANSWER =
            Pattern.compile("^\\s*Final Answer:", Pattern.MULTILINE);

    /**
     * Stop once we've hit the step budget or the LLM has emitted a
     * line starting with "Final Answer:".
     */
    protected boolean shouldStop(String lastResponse, int step) {
        if (step >= stepBudget - 1) {
            return true;
        }
        return lastResponse != null && FINAL_ANSWER.matcher(lastResponse).find();
    }

    private static final Pattern ACTION =
            Pattern.compile("^\\s*Action:[ \\t]*(.*)$", Pattern.MULTILINE);
    private static final Pattern ACTION_INPUT =
            Pattern.compile("^\\s*Action Input:[ \\t]*(.*)$", Pattern.MULTILINE);

    /**
     * Parse the first "Action: <name>" / "Action Input: <arg>" pair out of
     * {@code response} and return them as a (name, arg) entry, or null if
     * there is no Action line. A missing Action Input means an empty
     * argument; one pair of surrounding double quotes is stripped.
     */
    protected Map.Entry<String, String> parseAction(String response) {
        if (response == null) {
            return null;
        }
        Matcher action = ACTION.matcher(response);
        if (!action.find()) {
            return null;
        }
        String name = action.group(1).trim();
        if (name.isEmpty()) {
            return null;
        }
        String arg = "";
        Matcher input = ACTION_INPUT.matcher(response);
        if (input.find(action.end())) {
            arg = input.group(1).trim();
            if (arg.length() >= 2 && arg.startsWith("\"") && arg.endsWith("\"")) {
                arg = arg.substring(1, arg.length() - 1);
            }
        }
        return Map.entry(name, arg);
    }
}
