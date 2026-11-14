# Compose Build Fix v4

Fixed the remaining Kotlin/Compose syntax issue in `AuthorityTeamsScreen` and other Material3 button calls.

The root cause was Kotlin named arguments followed by positional arguments, for example:

`Button(enabled = ..., onClick = ..., Modifier.fillMaxWidth())`

which is invalid Kotlin. These calls now use named `modifier = ...` arguments consistently.

The Rescue Teams registration button was also missing the closing `}` for its `onClick` lambda after the previous automated edit; this has been corrected.

A syntax-only Kotlin parser check reports no remaining parser errors in `DisasterLinkApp.kt`.
