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

## Usage

| Command | What it does |
|---|---|
| `/explain <topic>` | Step-by-step explanation with an example |
| `/quiz <topic>` | 5-question multiple-choice quiz with answer key |
| `/flashcards <topic>` | 10 Q/A flashcards |
| `/summarize` | Paste notes, then type `END` on its own line |
| `/reset` | Clear conversation history |
| `/help` | Show commands |
| `/quit` | Exit |

Anything else you type is sent as a normal question, and the agent remembers the conversation so you can ask follow-ups.

```
> /explain Big-O notation
> can you give me a harder example?
> /quiz Big-O notation
```

## Project Structure

```
src/main/java/com/studyagent/
├── App.java                 # CLI entry point and command handling
├── agent/StudyAgent.java    # Study features and conversation history
├── config/AppConfig.java    # Loads GEMINI_API_KEY / GEMINI_MODEL from .env
└── llm/
    ├── GeminiClient.java    # Calls the Gemini generateContent REST API
    └── Message.java         # A single user/model conversation turn
src/test/java/com/studyagent/  # JUnit 5 tests (no API key or network needed)
```

## Troubleshooting

- **`GEMINI_API_KEY is not set`**: make sure `.env` exists in the project root and contains your real key, not the placeholder.
- **`Gemini API error 400/403`**: check that the key is valid and the Generative Language API is enabled for it.
- **`Gemini API error 404`**: the model name in `GEMINI_MODEL` is wrong or not available to your key.
- **`Gemini API error 429`**: you've hit the rate limit or quota; wait a moment and try again.
