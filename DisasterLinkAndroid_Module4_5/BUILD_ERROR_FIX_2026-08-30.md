# Build error fixes

Fixed in `ui/DisasterLinkApp.kt`:

1. RescueTaskDetailScreen: corrected Button argument order by using `modifier = Modifier.fillMaxWidth()` after named `enabled`.
2. AuthorityScreen: restored the missing `session` local value.
3. AuthorityScreen: corrected Button argument order for the rescue-team registration button.
4. TaskSummary/StatCard: made `StatCard` a `RowScope` composable so `Modifier.weight(1f)` is valid.
5. FeedbackScreen: corrected Button argument order after named `enabled`.

These fixes address the errors visible in Android Studio such as:
- Inapplicable candidate(s): fun Button(...)
- @Composable invocations can only happen from the context of a @Composable function
- Unresolved reference: session
- Expression 'weight' of type kotlin.Float cannot be invoked
