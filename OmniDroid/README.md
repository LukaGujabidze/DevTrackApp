# OmniDroid

**A local-first Android operator prototype.** This is a real Kotlin/Compose project scaffold with a working deterministic command-to-tool pipeline.

## Implemented in v0.1

- Jetpack Compose chat-style interface
- Command parser for a small, explicit command set
- Agent orchestrator separated from Android tools
- Risk classification and policy boundary
- Launch an installed, launchable app by visible label
- Open Android Settings
- List apps visible through Android's launcher-intent package visibility
- Basic result reporting

## Run

1. Open this directory in Android Studio (recent version).
2. Use JDK 17.
3. Allow Gradle sync to complete.
4. Connect an Android device or start an emulator.
5. Run the `app` configuration.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35 and min SDK 26.

## Try

- `open settings`
- `open calculator`
- `list apps`

App names depend on the device and installed packages.

## Architecture

```text
Compose UI
   |
AgentOrchestrator
   |---- CommandParser
   |---- PolicyEngine
   |
AndroidToolRegistry
   |---- launch app
   |---- open system settings
   |---- list launchable apps
```

## Important limitations

- This is a working MVP, not the complete autonomous operator.
- Android package visibility means the app can only see packages allowed by its manifest visibility declarations. This build queries launcher activities.
- Launching an app is not the same as controlling its internal UI.
- Protected settings and destructive actions need Android-supported user confirmation or privileged deployment.
- There is intentionally no AccessibilityService, LLM, backend, or broad file access in this first build. Those should be added as separate, reviewable capabilities.

## Next engineering milestones

1. Add unit tests for parsing, policy and orchestration.
2. Add a confirmation state machine for sensitive actions.
3. Add Room task history.
4. Add an `LlmProvider` interface and OpenAI adapter with strict tool schemas.
5. Add an AccessibilityService only for user-authorized UI automation, with visible enablement and a stop control.
6. Add file tools using Android's Storage Access Framework.
