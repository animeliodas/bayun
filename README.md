# Bayun — a free reader that reads aloud and translates

**English** · [Русский](README.ru.md)

Bayun (after Kot Bayun, the storytelling cat of Russian fairy tales) is an open-source Android reader for people
who listen to books as much as they read them:

- **Read aloud with the screen off** — no subscription. Uses the phone's own speech engine (Google, Samsung,
  RHVoice…), keeps playing in the background with a media notification, lock-screen and headset controls, a sleep
  timer, adjustable speed and pitch; the current sentence is highlighted and the text follows the voice. Choose the
  speech engine and a voice for each language of your books (offline/online voices are marked). Sentences are
  synthesized ahead and played by the app itself, so headset and watch buttons work, pause resumes mid-sentence,
  and a short network hiccup does not switch an online voice to the offline one.
- **Translate books into your language** — EPUB, FB2 and TXT in any language are translated chapter by chapter by
  the [Polytome](https://github.com/animeliodas/polytome) engine (embedded in the app), which checks names,
  character gender, numbers and omissions with code, not just the model.
- **Read while it translates** — one "Book translation" panel per book: translate manually, the chapter you open,
  or keep any number of chapters ahead of you; translate the whole book now or every night from a time you choose
  until it is done (starting from the chapter you are reading; an interrupted translation continues by itself).
  Reading aloud waits for the next chapter's translation instead of switching to the original.
- **Your language first** — the title, author and contents are translated as soon as a book is added (and the first
  chapters too, if you like), so the library never shows a title you cannot read; the original stays one tap away.
- **Pick a model from the provider's live list** — with prices per 1M tokens and context size, search,
  "free only" and "cheapest first"; or type any model id by hand. "Check model" translates a short shared test
  text and scores it with Polytome's code checks (names, gender, numbers, omissions), with time and cost; the result
  can be shared to the [common model table](https://github.com/animeliodas/polytome/blob/main/bench/LEADERBOARD.md).
- **Original and translation side by side** — tap a paragraph to see the original under the translation;
  long-press to start reading aloud from there.
- **A reader that stays out of the way** — tap the text to hide the panels. Long-press a sentence and slide:
  ↑ read aloud from here, ← bookmark, → ask the AI about a word, name or idiom (it knows the book's glossary),
  ↓ fix the translation of this paragraph (see the new version, edit it, save); the center button shows the original.
- Library with covers, reading position and bookmarks per book, light/sepia/dark themes, five accent colors or your
  wallpaper's (Material You), adjustable font size.
- Interface in English and Russian.

## Status

Early version (0.1). Import (EPUB/FB2/TXT), reading and read-aloud in the background (app minimised, forced Doze) are
verified on a real phone (Android 16); chapter translation works there with a real provider (Ollama Cloud). Each
translation flow (title and contents, translate ahead, the whole book from the current chapter, night translation,
stop and resume, the model list, the AI help in the reader) was also checked step by step against a local
OpenAI-compatible test server. Translation needs an API key of an LLM provider (OpenRouter, Ollama Cloud, OpenAI,
Anthropic, Gemini, DeepSeek…) or your own OpenAI-compatible server (Ollama / LM Studio on your computer).

## Build

Requirements: Android Studio (Android SDK 37), Python 3.12 on the build machine (used by Chaquopy to install the
engine's pure-Python packages).

```
tools\update-polytome.cmd [path]    # only to update the engine: rebuild app\wheels from a Polytome checkout (default ..\polytome)
gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
gradlew installDebug                 # install on a connected phone
```

## How it works

```
app/src/main/java/io/github/animeliodas/bayun/
  engine/PolytomeEngine.kt   bridge to Polytome (Python via Chaquopy): JSON calls + events
  data/                      library, chapter text, settings, encrypted API keys (Android Keystore)
  tts/ReadAloud.kt           read-aloud engine: sentences, queue, highlight, audio focus, wake lock
  tts/PlaybackService.kt     Media3 session: notification, lock screen, headset buttons
  translate/                 translation queue (whole book, read-ahead, resume) and its foreground service
  ui/                        Compose screens: library, reader, settings
```
Books are stored as Polytome workspaces (chapters as paragraphs, original and translation 1:1), so the same book
folder can be continued with the Polytome CLI on a computer.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
