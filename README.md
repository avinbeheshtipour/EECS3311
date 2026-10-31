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
   You can also choose a different model (the default is `gemini-2.5-flash`):
   ```
   GEMINI_MODEL=gemini-2.5-pro
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

| Command | What it does | Status |
|---|---|---|
| `ask <question>` | Ask a question; answers draw on any uploaded material | Available (you can also just type the question) |
| `upload <file>` | Add a PDF or TXT file of course material to the knowledge base | Available |
| `quiz <topic>` | Generate a quiz on a topic | Planned |
| `flashcard <topic>` | Generate flashcards on a topic | Planned |
| `plan <topic> <days>` | Build a study plan for a topic over a number of days | Planned |
| `summarize <file>` | Summarize a PDF or TXT file | Planned |
| `progress` | Show your study progress | Planned |
| `export` | Export your study material | Planned |
| `recommend` | Recommend what to study next | Planned |
| `graph <topic>` | Show how concepts in a topic relate | Planned |
| `history` | Show your past questions and answers | Planned |
| `quit` | Exit the program | Available as `/quit` |

`/help` lists the commands available in the current build.

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
├── CLIView.java              # View: command-line interface (main class)
├── MainGUI.java              # View: Swing window (Upload button so far)
├── StudyController.java      # Controller: routes CLI and GUI requests to StudyService
├── StudyService.java         # Model: study features (Q&A, uploads)
├── KnowledgeRepository.java  # Parses PDF/TXT files into Chunks
├── Chunk.java                # A ~200-word piece of uploaded material
├── RAGEngine.java            # Indexes chunks and retrieves the most relevant ones
├── Prompt.java               # Builder pattern: assembles prompts
├── LLMFacade.java            # Facade pattern: single entry point to the LLM
├── GeminiProxy.java          # Proxy pattern: caching and retries
└── GeminiClient.java         # Singleton pattern: the Gemini API connection
```

## Troubleshooting

- **`GEMINI_API_KEY is not set`**: make sure `.env` exists in the project root and contains your real key, not the placeholder.
- **`Gemini API error 400/403`**: check that the key is valid and the Generative Language API is enabled for it.
- **`Gemini API error 404`**: the model name in `GEMINI_MODEL` is wrong or not available to your key.
- **`Gemini API error 429`**: you've hit the rate limit or quota; wait a moment and try again.
