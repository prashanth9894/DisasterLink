# DisasterLink Compile Error Fix v5

This version fixes the errors visible in the Android Studio screenshot.

## Main fix
In `AuthorityTeamsScreen`, `Button` was using a named argument (`enabled = ...`) followed by a positional `Modifier`, which Kotlin does not allow. The call is now consistently written with named arguments:

```kotlin
Button(
    onClick = { ... },
    modifier = Modifier.fillMaxWidth(),
    enabled = canManage,
    colors = ButtonDefaults.buttonColors(containerColor = Navy)
) { ... }
```

The resulting parser/cascade errors such as `@Composable invocations can only happen...`, `Unresolved reference: Row`, and `Unresolved reference: verticalAlignment` are addressed by fixing the preceding malformed call.

Open this project as a fresh Android Studio project rather than editing the old copy.
