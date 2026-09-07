In Clojure, prefer function composition using `->`, `->>` and `comp` or `partial`. Avoid `loop`/`recur` if you can use `reduce`. All functions should be pure and side-effect-free. Never use an `@atom` to collect data inside a function. You can use an `@atom` only to store the global state of the application.

Use built-in core functions (`map`, `filter`, `reduce`, comprehensions, set operations) and avoid nested loops where possible.

If you see potentially inefficient usage of `concat` or `lazy-seq`, warn about it and suggest an alternative using transducers."

Prefer idiomatic Clojure string functions from `clojure.string` over direct Java method calls on `String`.

Always describe using types in function docs, and/or specify type for arguments. Docstrings on `defn` required for public API functions.

Functions not part of the public API should be `defn-`.