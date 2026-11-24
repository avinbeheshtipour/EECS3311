# AI Study Agent

A study assistant for university students, written in Java, with both a command-line interface (CLI) and a desktop window (GUI). It uses Google Gemini to answer questions about your uploaded course notes, summarize documents, generate quizzes, flashcards, study plans and concept graphs, and explain quiz answers. It also tracks your results, finds your weakest topics, picks quiz difficulty from your accuracy, and recommends what to study next.

EECS3311 Course Project.

| Stage 2 deliverable | Where |
|---|---|
| Implementation report | [`stage2-report.pdf`](stage2-report.pdf) |
| Demonstration video | <https://youtu.be/qk7mwRH_6Yk> (feature timestamps in the report, Section 7) |
| Demonstration video 2: same task in the GUI, then the standalone CLI | _(link to be added)_ |
| Updated UML diagrams | [`Stage2/`](Stage2/) (index in [`Stage2/README.md`](Stage2/README.md)) |
| Stage 1 report and diagrams | [`Stage1/`](Stage1/) |

## Repository Layout

```
.
├── README.md             this file: setup, running, commands
├── stage2-report.pdf     Stage 2 implementation report
├── pom.xml               Maven build (Java 17)
├── .env.example          copy to .env and add your Gemini key (.env is gitignored)
├── sample_notes.txt      sample course notes for trying the app
├── tasks/sample-task.txt sample task for the standalone CLI (see Standalone CLI below)
├── src/main/java/com/studyagent/   all source code (see Project Structure below)
├── src/test/java/com/studyagent/   JUnit tests (run by mvn clean package)
├── Stage1/               Stage 1 report (PDF) and UML diagrams
└── Stage2/               Stage 2 updated UML diagrams
```

## Requirements

- Java 17 or newer (`java -version`)
- Apache Maven 3.8+ (`mvn -v`)
  - macOS: `brew install maven`
  - Windows: `winget install Apache.Maven` or download from https://maven.apache.org/download.cgi
  - Linux: `sudo apt install maven` (Debian/Ubuntu)
- A Gemini API key from https://aistudio.google.com/apikey

## Setup

1. **Clone the project and enter the folder**
   ```bash
   git clone https://github.com/avinbeheshtipour/EECS3311.git
   cd EECS3311
   ```

2. **Create your `.env` file**
   ```bash
   cp .env.example .env
   ```
   On Windows (Command Prompt) use `copy .env.example .env`.

   Open `.env` and replace `your_key_here` with your real key:
   ```
   GEMINI_API_KEY=AIza...
   ```
   You can also choose a different model (the default is `gemini-3.5-flash-lite`):
   ```
   GEMINI_MODEL=gemini-3.8-flash
   ```
   `.env` is listed in `.gitignore`, so your key is never committed. Environment variables set in your shell take precedence over values in `.env`.

3. **Build and run the tests**
   ```bash
   mvn clean package
   ```

## Running

Run it straight from Maven:
```bash
mvn -q exec:java
```

Or run the packaged jar:
```bash
java -jar target/ai-study-agent.jar
```

Run from the project root so the `.env` file is found.

To open the desktop window instead of the CLI:
```bash
java -jar target/ai-study-agent.jar -gui
```
or, from Maven:
```bash
mvn -q exec:java -Dexec.mainClass=com.studyagent.MainGUI
```

## Standalone CLI

The agent can also run a whole task straight from a terminal, reading the commands from a file or from standard input instead of the keyboard:

```bash
java -jar target/ai-study-agent.jar -LLMAPI gemini -input tasks/sample-task.txt
```

Run it from the project root, so `.env` and the files named in the task are found.

| Option | What it does |
|---|---|
| `-input <file>` | Run the commands in `<file>`, one per line. Use `-` to read them from standard input |
| `-output <file>` | Write all output to `<file>` instead of the terminal |
| `-model <name>` | Gemini model to use, e.g. `gemini-3.5-flash-lite`; overrides `GEMINI_MODEL` in `.env` |
| `-LLMAPI <name>` | LLM API to use. Only `gemini` is supported (the default); any other value is an error |
| `-gui` | Open the desktop window instead of the command line |
| `-help` | Show the options and exit |

Option names are not case-sensitive. The API key always comes from `GEMINI_API_KEY` in `.env`; it can't be given on the command line.

**Task file.** A task file holds the same commands you would type in the CLI (see [CLI Commands](#cli-commands)), one per line. This is [`tasks/sample-task.txt`](tasks/sample-task.txt):

```
upload sample_notes.txt
ask What is the time complexity of array access?
plan linked lists 3
graph linked lists
quiz linked lists
A
B
C
D
A
progress
```

The five lines after `quiz linked lists` are the quiz answers (A–D, or an empty line to skip a question). The line after the answers may be `explain N`; any other command, like `progress` here, simply runs next.

**How a task runs.** With `-input` or `-output`, or when standard input isn't a terminal (for example `java -jar target/ai-study-agent.jar < tasks/sample-task.txt`), the agent runs without stopping for input:
- no banner, help text or prompts are shown;
- each command is printed as `> command` before its output;
- `flashcard <topic>` prints the cards instead of starting a review;
- `quit` ends the task early.

With no options in a terminal, the CLI is interactive exactly as described in [Running](#running).

More examples:
```bash
# read the task from standard input and save the result to a file
java -jar target/ai-study-agent.jar -input - -output result.txt < tasks/sample-task.txt

# use a different Gemini model for this run
java -jar target/ai-study-agent.jar -model gemini-3.8-flash -input tasks/sample-task.txt
```

| Exit code | Meaning |
|---|---|
| 0 | Every command succeeded |
| 1 | At least one command failed (its `Error: ...` message is in the output) |
| 2 | Invalid options, an unsupported `-LLMAPI`, or an input/output file that can't be opened |

## CLI Commands

| Command | What it does |
|---|---|
| `ask <question>` | Ask a question; answers draw on any uploaded material (you can also just type the question) |
| `upload <file>` | Add a PDF or TXT file of course material to the knowledge base |
| `quiz <topic>` | Take a 5-question multiple-choice quiz, then see your score and corrections |
| `explain <N>` | After a quiz, at the `explain>` prompt: explain why the answer to question N is correct |
| `adaptive <topic>` | Take a quiz whose difficulty (Easy/Medium/Hard) is chosen from your accuracy so far |
| `flashcard <topic>` | Review 10 flashcards; cards you miss come back until you get them right |
| `plan <topic> <days>` | Break a topic into day-by-day milestones, e.g. `plan recursion 7` |
| `summarize <file>` | Summarize a PDF or TXT file: each ~200-word chunk in 3 bullet points, then merged into one summary |
| `progress` | Show your quiz scores and flashcard accuracy for this session |
| `export [md\|pdf] [file]` | Save your latest study plan or progress summary as Markdown (default) or PDF |
| `weaktopics` | List every topic you've practised with its accuracy, weakest first |
| `recommend` | Find your weakest topic from quiz and flashcard results and get a focused study approach |
| `graph <topic>` | Show how the concepts in a topic connect (as an adjacency list) |
| `history` | Show the questions you've asked this session and their answers |
| `help` | List all commands |
| `quit` | Exit the program (`exit` also works) |

Every command also works with a leading slash, e.g. `/quiz recursion`. Type `quit` during a quiz or flashcard session to stop it early.

## Desktop Window

The window has a tab for each feature: **Ask**, **Quiz**, **Flashcards**, **Study Plan**, **Summarize**, **Graph**, **Progress**, **Recommend** and **History**. The Quiz tab has an **Adaptive difficulty** checkbox and an **Explain...** button, and the Progress tab has a **Weak Topics** button. The toolbar's **Upload** button adds course material, and **Export** saves your latest study plan or progress summary as Markdown or PDF.

## Example Session

`sample_notes.txt` in the project root contains sample course notes you can try:
```
> upload sample_notes.txt
Uploaded sample_notes.txt (4 chunks).
> What is the time complexity of array access?
> quiz linked lists
  ... answer A-D for each question, then:
explain> explain 2
> weaktopics
> adaptive linked lists
> plan hash tables 5
> export pdf
> recommend
> quit
```

Uploaded files are split into chunks of about 200 words. When you ask a question, the 3 chunks that best match its keywords are sent to Gemini as context. Scanned PDFs (images of text) can't be read.

## Project Structure

Feature IDs match the Stage 2 report: F = feature from the Stage 1 report, N = new feature.

```
src/main/java/com/studyagent/
├── CLIView.java                  # View: command-line interface (default main class)
├── CommandLineOptions.java       # View: parses the standalone CLI options (-input, -output, -model, -LLMAPI, -gui, -help)
├── MainGUI.java                  # View: Swing window with a tab per feature
├── StudyController.java          # Controller (MVC): routes CLI and GUI requests to StudyService
├── StudyService.java             # Model: entry point for every study feature
│
├── KnowledgeRepository.java      # F02: parses PDF/TXT files into Chunks
├── Chunk.java                    # A ~200-word piece of uploaded material
├── RAGEngine.java                # F05: indexes chunks, retrieves the most relevant ones
├── LearningActivityFactory.java  # Factory pattern: builds Quiz, Flashcard, StudyPlan from LLM output
├── Quiz.java                     # F08/F09/F10: multiple-choice quiz, grading, explanations
├── Flashcard.java                # F07: card with spaced-repetition scoring
├── StudyPlan.java                # F06: day-by-day milestones
├── SummarizationService.java     # F03: chunk-by-chunk document summaries
├── ProgressRepository.java       # N1: quiz scores and flashcard accuracy
├── ExportService.java            # N2: Markdown and PDF export
├── RecommendationService.java    # N3/F11/F12: weakest topic, weak-topic list, adaptive difficulty
├── KnowledgeGraph.java           # N4: concept graph as an adjacency list
├── ConversationMemory.java       # N5: Q&A history
│
├── Prompt.java                   # Builder pattern: assembles prompts
├── LLMFacade.java                # Facade pattern: single entry point to the LLM
├── LLMClient.java                # Interface shared by GeminiProxy and GeminiClient
├── GeminiProxy.java              # Proxy pattern: caching and retries
└── GeminiClient.java             # Singleton pattern: the Gemini API connection
```

## Troubleshooting

- **`GEMINI_API_KEY is not set`**: make sure `.env` exists in the project root and contains your real key, not the placeholder.
- **`Gemini API error 400/403`**: check that the key is valid and the Generative Language API is enabled for it.
- **`Gemini API error 404`**: the model name in `GEMINI_MODEL` (or `-model`) is wrong or not available to your key.
- **`Gemini API error 429`**: you've hit the rate limit or quota; wait a moment and try again.
