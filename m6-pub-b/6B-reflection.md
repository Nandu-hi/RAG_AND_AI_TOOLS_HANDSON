# Session 6B Reflection

Setup: ReAct `AgentDriver` + `llama3.2:3b` via Ollama (temperature 0), step
budget 6. Query: how many books Ursula K. Le Guin wrote in the 1970s.
Correct answer: **1** (*The Dispossessed*, 1974).

| Run | Author in query | Tool calls | Iterations | Outcome |
|-----|-----------------|------------|------------|---------|
| 1 | "Ursula Le Guin" | search ×2 → `[]` | 3 | `Final Answer: []` (wrong) |
| 2 | "Ursula K.Le Guin" | search ×2 (hit), list, search, list, list | 6 | Step budget hit, no answer |
| 3 | "Ursula K.Le Guin" | search `\|1970\|1979`, list ×3 | 4 + crash | Timeout |

## Which tool did the agent prefer, and why?

- `search_books` came first every run. The prompt calls `list_books`
  "expensive", and "author + decade" maps directly onto `author|from|to`.
- After the first step the preference collapsed (7 search vs 6 list calls).
  The 3B model ignored its observations and copied the previous Thought word
  for word. In run 2 it said "use search_books" while calling `list_books`.
- Run 1 emitted `Action` and `Final Answer` in one response. Run 2 had the
  answer after one call but never stopped.
- Runs 2 and 3 had the same first prompt but made different first calls, even
  at temperature 0. The behaviour is fragile, not a stable preference.

## How much did tool-description quality matter?

- **Helped:** the explicit format with an example meant every search call was
  well-formed.
- nothing said the author is matched as part of the
  name, so run 1's "Ursula Le Guin" got `[]`. A hint like "use the surname"
  would have fixed it. Nothing said what to do on `[]` (relax the filter) or
  on a hit (answer now).

## Why run 3 timed out

- langchain4j's default Ollama timeout is 60 s.
- Each `list_books` call adds the whole catalogue (3.3 KB, ~840 tokens) to the
  scratchpad, which is re-sent every turn.
- By call 5 the prompt was ~2,900 tokens. The local 3B model couldn't respond
  in 60 s, so the request timed out (`readResponseHeaders`).

## What a production driver needs

1. Loop detection: flag repeated `(tool, args)` pairs, then nudge or stop.
2. Cap, summarise or deduplicate large observations so the context stays
   bounded.
3. Exact token usage from the provider, plus time budgets.
