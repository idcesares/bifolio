# Diagrams

A Mermaid fence is shown as source with a diagram label and is not rendered.

```mermaid
graph TD
    A[Open file] --> B{Is it Markdown?}
    B -- yes --> C[Parse]
    B -- no --> D[Show error]
    C --> E[Paginate]
```

A normal code block follows, which must not get the diagram label.

```kotlin
fun main() {
    println("hello")
}
```
