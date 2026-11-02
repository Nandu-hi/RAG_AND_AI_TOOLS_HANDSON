# RAG and AI Skills Hands-on

This repository contains two Module 6 hands-on projects that demonstrate
different ways to build an AI assistant in Java:

- **M6-pub-a** is a retrieval-augmented generation (RAG) system over a local
  Java repository.
- **M6-pub-b** is a ReAct-style query assistant that uses skills (tools) to
  answer questions about a book catalogue.

Both projects are self-contained and use Java 17, `make`, and JUnit. The
projects can be tested locally, and they can optionally be connected to an
Ollama model running on the developer's machine.

## Repository layout

```text
.
├── m6-pub-a/                 # RAG assistant over sample-repo/
│   ├── src/                  # Indexing, chunking, retrieval, and LLM code
│   ├── test/                 # JUnit tests
│   ├── sample-repo/          # Java repository used as the RAG corpus
│   ├── prompts/rag.txt       # RAG prompt template
│   └── solution/             # Reference solution materials, when provided
└── m6-pub-b/                 # Tool-using book query assistant
    ├── src/                  # Agent loop and tool implementations
    ├── test/                 # JUnit tests
    ├── books.json            # Book catalogue
    ├── prompts/react.txt     # ReAct prompt template
    └── solution/             # Reference solution
```

## Prerequisites

- Java Development Kit (JDK) 17 or newer
- GNU Make
- `curl` (used to download the JUnit console launcher)
- Maven, only when installing the optional LangChain4j dependencies
- Ollama, only when running the real local LLM integrations

Check the Java version before building:

```bash
java -version
javac -version
```

## M6-pub-a: RAG over a local Java repository

The RAG pipeline indexes the Java files under `m6-pub-a/sample-repo/`,
splits them into chunks, embeds the chunks, retrieves the most relevant
chunks for each question, and passes the retrieved context to an LLM.

Important classes include:

- `RepoIndexer` for file discovery, chunking, and indexing
- `Embedder` and `MiniLmEmbedder` for vector representations
- `RagAssistant` for retrieval and answer generation
- `prompts/rag.txt` for the answer prompt

Run the tests:

```bash
cd m6-pub-a
make deps
make test
```

Build and run the complete assistant against the sample repository:

```bash
make langchain4j
make run
```

The `langchain4j` target downloads the embedding model, Ollama integration,
and their transitive dependencies into the ignored `libs/langchain4j/`
directory. The current `Main` class uses the bundled MiniLM embedder and an
Ollama chat model, so Ollama must be running for the full interactive run.

To index another directory:

```bash
make run ARGS=/absolute/path/to/java-repository
```

## M6-pub-b: Query assistant using skills

The second project implements a ReAct loop. The model receives a query and
can call registered skills (tools) before producing a final answer.

The built-in skills are:

- `ListBooksTool`, which lists catalogue entries from `books.json`
- `SearchBooksTool`, which searches by author and year range

`AgentDriver` maintains the scratchpad, parses `Action` and `Action Input`
lines, records tool observations, and stops when the model emits a final
answer or a budget is exhausted. The step and token budgets are configured
in `src/Main.java`.

Run the tests:

```bash
cd m6-pub-b
make deps
make test
```

Install the optional LangChain4j dependencies and ask a question with Ollama:

```bash
make langchain4j
make run "Which books mention agents?"
```

If no query is supplied, the default query is:

```text
Which books mention agents?
```

The agent prompt and tool protocol are defined in `prompts/react.txt`.
Execution traces from representative runs are stored in `traces/`.

## Running with Ollama

1. Install and start [Ollama](https://ollama.com/).
2. Pull the model configured by the module you want to run:

   ```bash
   ollama pull llama3.2:3b
   ```

3. From the selected module, run `make langchain4j` followed by `make run`.

The applications connect to Ollama at `http://localhost:11434`. No cloud API
key is required. The default tests are designed to exercise the core logic
without requiring a running Ollama server.

## Reference solutions

The reference implementation for M6-pub-b is under `m6-pub-b/solution/`.
Its README explains how to copy the solution sources and tests into the
starter project. M6-pub-a also contains solution materials where available.

To remove generated build output and downloaded dependencies from either
module:

```bash
make clean
```

## Learning goals

Together, the projects cover:

- Chunking source files into retrieval units
- Embedding and similarity-based retrieval
- Prompting an LLM with retrieved context
- Designing a tool/skill interface for an agent
- Parsing model actions and managing a ReAct scratchpad
- Enforcing step and token budgets
- Testing AI application components without network access
