# AI Study Agent

A command-line study assistant written in Java that uses Google Gemini to explain topics, generate quizzes and flashcards, summarize notes, and answer follow-up questions with conversation memory.

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
   git clone <your-repo-url>
   cd ai-study-agent
   ```

2. **Create your `.env` file**
   ```bash
   cp .env.example .env
   ```
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
mvn -q exec:java -Dexec.mainClass=com.studyagent.MainGUI
```

## CLI Commands

| Command | What it does |
|---|---|
| `ask <question>` | Ask a question; answers draw on any uploaded material (you can also just type the question) |
| `upload <file>` | Add a PDF or TXT file of course material to the knowledge base |
| `quiz <topic>` | Take a 5-question multiple-choice quiz, then see your score and corrections |
| `flashcard <topic>` | Review 10 flashcards; cards you miss come back until you get them right |
| `plan <topic> <days>` | Break a topic into day-by-day milestones, e.g. `plan recursion 7` |
| `summarize <file>` | Summarize a PDF or TXT file: each ~200-word chunk in 3 bullet points, then merged into one summary |
| `progress` | Show your quiz scores and flashcard accuracy for this session |
| `export [md\|pdf] [file]` | Save your latest study plan or progress summary as Markdown (default) or PDF |
| `recommend` | Find your weakest topic from quiz and flashcard results and get a focused study approach |
| `graph <topic>` | Show how the concepts in a topic connect (as an adjacency list) |
| `history` | Show the questions you've asked this session and their answers |
| `help` | List all commands |
| `quit` | Exit the program (`exit` also works) |

Every command also works with a leading slash, e.g. `/quiz recursion`. Type `quit` during a quiz or flashcard session to stop it early.

## Desktop Window

The window has a tab for each feature: **Ask**, **Quiz**, **Flashcards**, **Study Plan**, **Summarize**, **Graph**, **Progress**, **Recommend** and **History**. The toolbar's **Upload** button adds course material, and **Export** saves your latest study plan or progress summary as Markdown or PDF.

Example session:
```
> upload lecture3.pdf
Uploaded lecture3.pdf (12 chunks).
> What is the difference between an abstract class and an interface?
```

Uploaded files are split into chunks of about 200 words. When you ask a question, the 3 chunks that best match its keywords are sent to Gemini as context. Scanned PDFs (images of text) can't be read.

## Project Structure

```
src/main/java/com/studyagent/
├── CLIView.java                  # View: command-line interface (default main class)
├── MainGUI.java                  # View: Swing window with a tab per feature
├── StudyController.java          # Controller (MVC): routes CLI and GUI requests to StudyService
├── StudyService.java             # Model: entry point for every study feature
│
├── KnowledgeRepository.java      # F02: parses PDF/TXT files into Chunks
├── Chunk.java                    # A ~200-word piece of uploaded material
├── RAGEngine.java                # F01: indexes chunks, retrieves the most relevant ones
├── LearningActivityFactory.java  # Factory pattern: builds Quiz, Flashcard, StudyPlan from LLM output
├── Quiz.java                     # F03: multiple-choice quiz with grading
├── Flashcard.java                # F04: card with spaced-repetition scoring
├── StudyPlan.java                # F05: day-by-day milestones
├── SummarizationService.java     # F06: chunk-by-chunk document summaries
├── ProgressRepository.java       # F07: quiz scores and flashcard accuracy
├── ExportService.java            # F08: Markdown and PDF export
├── RecommendationService.java    # F09: suggestions for the weakest topic
├── KnowledgeGraph.java           # F11: concept graph as an adjacency list
├── ConversationMemory.java       # F12: Q&A history
│
├── Prompt.java                   # Builder pattern: assembles prompts
├── LLMFacade.java                # Facade pattern: single entry point to the LLM
├── GeminiProxy.java              # Proxy pattern: caching and retries
└── GeminiClient.java             # Singleton pattern: the Gemini API connection
```

## Troubleshooting

- **`GEMINI_API_KEY is not set`**: make sure `.env` exists in the project root and contains your real key, not the placeholder.
- **`Gemini API error 400/403`**: check that the key is valid and the Generative Language API is enabled for it.
- **`Gemini API error 404`**: the model name in `GEMINI_MODEL` is wrong or not available to your key.
- **`Gemini API error 429`**: you've hit the rate limit or quota; wait a moment and try again.
