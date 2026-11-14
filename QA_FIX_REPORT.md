# DisasterLink — Senior QA / Bug-Fix Report

## Status

**Implementation completed for the supplied source package.**

### Automated result

```text
DISASTERLINK QA: ALL PASS
```

## Critical defects fixed

### 1. Database module corruption
`db/db.js` contained rescue-team router code and therefore could not provide the `db`, authority-rank constants, or `rankIndex` expected by the rest of the backend. It was reconstructed as the actual SQLite initialization module.

### 2. Team registration used the wrong source of truth
The Android Authority flow previously required rescue members to exist in the authority phone's local Room cache. Team creation now calls `POST /api/teams`; the backend verifies each government/employee ID against its central `users` table and confirms the account has role `RESCUE`.

### 3. Team ID vs rescue member ID confusion
`assignedTeamId` is now consistently treated as the rescue-team UUID. Rescue government IDs remain member identities only.

### 4. Duplicate active task creation
The backend now prevents a second non-terminal task from being created for the same emergency. Assignment changes use reassignment rather than creating competing tasks.

### 5. Offline task reassignment was not synchronized
A previously synchronized task has no `clientId`. The old sync code therefore marked an offline reassignment as synced without calling the server. Synchronization now pushes `ASSIGNED` changes with `/reassign` even when the task was originally created on the server.

### 6. Offline LoRa SOS could create a second emergency
Android creates a local emergency with a stable `clientId`. The exact same client ID is now sent through BLE to the field ESP32 and over LoRa. If LoRa delivery reaches the backend before normal Android synchronization, the later Android sync finds the existing record instead of creating a duplicate.

### 7. Emergency status mismatch
Android previously used `SOS` as an `EmergencyStatus`, while the backend uses `REPORTED`. Android now uses `REPORTED` consistently.

### 8. Room schema mismatch
`AppDao` queried `EmergencyEntity.clientId`, but the entity did not contain that field. The entity now contains `clientId`, the database version is 5, and migration 4 -> 5 adds the column and unique index.

### 9. Repository migration configuration
The main Repository database builder previously registered only the 2 -> 3 migration. It now registers 2 -> 3, 3 -> 4 and 4 -> 5. The WorkManager worker now delegates to Repository so there is one migration configuration.

### 10. Rescue UI falsely reported task actions
Accept/reject failures were not surfaced correctly. Repository methods now return `Result<Unit>`, and the UI only updates the displayed state after a successful operation or an intentional offline queue operation.

### 11. Invalid PENDING task action
Rescue UI no longer offers Accept for an unassigned PENDING task. The backend remains authoritative and rejects invalid transitions.

### 12. Unsafe team deletion
A team with an active task can no longer be deleted. Completed/rejected teams can be removed, and linked LoRa devices are unlinked automatically.

### 13. Duplicate rescue membership
A rescue member can no longer be added to multiple teams. This keeps government-ID -> team-ID resolution deterministic.

### 14. Invite registration integrity
Authority registration normalizes email/government ID/phone values and consumes invite codes transactionally. District-restricted invite codes must be used for the issued district.

### 15. Feedback duplication
A citizen can submit feedback only once for a specific task. A unique database index reinforces the server-side validation.

### 16. Field-node sequence persistence
The ESP32 field node now stores the next LoRa sequence number in NVS so a reboot does not normally reuse an earlier packet identity.

### 17. Secret leakage in deliverable
The original package contained live-looking SMTP/Wi-Fi/API secret values. The final distributable removes them and provides `.env.example`, `config.h.example`, and `secrets.h.example` templates.

## Automated test coverage

- Health endpoint
- Bootstrap invite creation
- Invited authority registration
- District-restricted invite validation
- Authority rank enforcement
- Rescue-team registration
- Duplicate rescue membership protection
- Invalid member ID validation
- Citizen emergency creation
- Team UUID task assignment
- Duplicate active-task prevention
- Rescue task visibility
- Rescue membership authorization
- Task acceptance
- Task reassignment
- Re-acceptance after reassignment
- Completion evidence minimum
- Task completion
- Completed-task reassignment protection
- Task audit events
- Citizen feedback
- Duplicate feedback protection
- Rescue team location update
- Invalid location validation
- LoRa device linking
- Gateway authentication
- LoRa SOS ingestion
- LoRa SOS deduplication
- Active team deletion protection
- Safe team deletion after task termination

## Real-hardware acceptance test

The following must still be run on the physical devices because it cannot be emulated reliably in this environment:

```text
Android
  ↓ BLE
Field ESP32 + Ra-02/SX1278
  ↓ LoRa
Gateway ESP32 + Ra-02/SX1278
  ↓ LAN/Wi-Fi
Node backend
  ↓
Authority Android
  ↓
Team assignment
  ↓
Rescue Android
```

Test both:

1. Backend available over local LAN.
2. Internet disabled while the local LAN/hotspot remains available.
3. Gateway backend unavailable, then restored, verifying NVS store-and-forward.
4. LoRa ACK loss/retry.
5. Duplicate LoRa packet.
6. Field-node reboot with sequence persistence.
