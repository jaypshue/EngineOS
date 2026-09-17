# EngineOS (Java)

This repository is the Java port of the existing EngineOS .NET prototype. It
preserves the prototype's domain vocabulary and conversation-first workflow
without translating its UI or provider implementations line by line.

The first vertical slice is intentionally headless:

1. Load a workspace and discover repository state.
2. Record human engineering intent.
3. Let the Engineer Agent propose one bounded Engineering Task.
4. Require explicit human approval.
5. Let the Coder Agent execute the approved Coding Task.
6. Record the execution result as Evidence.
7. Let the Engineer Agent review the evidence for presentation to the human.

Engineer and Coder use separate interfaces and provider records, so they can
later use different models or vendors.

Run the example with:

```text
mvn test
mvn package
java -cp target/classes com.engineos.EngineOsApplication C:\projects\EngineOS "Inspect the Java workspace"
```
