# Room/KSP Fix

Fixed the KSP error:

`Ambiguous getter for Field(... governmentId ...) All of the following match: getGovernmentId, getGovernmentId.`

Cause: `UserEntity` declared `governmentId` twice. Room/KSP saw two Kotlin properties producing the same Java getter.

Fix: removed the duplicate `governmentId` property from `UserEntity`; the original property remains at the first declaration.

No `@Ignore` is needed because both declarations were intended to represent the same database column.
