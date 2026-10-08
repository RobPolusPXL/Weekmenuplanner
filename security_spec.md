# Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Authentication Gate**: Unauthenticated callers (`request.auth == null`) cannot read or write any document in any collection.
2. **Household Membership Bound**: A `Household` document (`/households/{hid}`) can only be created with `members == [request.auth.uid]`, `createdBy == request.auth.uid`, and `inviteCode == hid`. A household can have at most 2 members (`members.size() <= 2`).
3. **Controlled Invite Join**: A second authenticated user not yet in `members` can update `/households/{hid}` only when `existing().members.size() == 1` by setting `members` to `[existing().members[0], request.auth.uid]` and updating `updatedAt`, leaving all other fields unchanged.
4. **Parent Gate on Subcollections**: Every operation (`get`, `list`, `create`, `update`, `delete`) on `/households/{hid}/dishes/{dishId}`, `/households/{hid}/days/{dayId}`, `/households/{hid}/weeks/{weekStart}`, and `/households/{hid}/looseItems/{itemId}` requires `request.auth.uid in get(/databases/$(database)/documents/households/$(hid)).data.members`.
5. **Immutable Audit Fields**: `createdBy`, `createdAt`, and `inviteCode` are immutable on updates across all entities that define them.
6. **Temporal Integrity**: All `createdAt` and `updatedAt` fields must be valid Firestore `timestamp` objects with `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("households").get()` -> Reject (`PERMISSION_DENIED`).
2. **Cross-Household Read**: Outsider `charlie` reads `/households/ROBJOKE` or `/households/ROBJOKE/dishes/d1` -> Reject.
3. **Household Creation Spoofing**: `alice` creates `/households/ROBJOKE` with `createdBy: "bob"` or `members: ["bob"]` -> Reject.
4. **Third Member Overflow**: `charlie` attempts to join `/households/ROBJOKE` when `members` already has `["alice", "bob"]` -> Reject.
5. **Hostile Takeover on Join**: `bob` joins `/households/ROBJOKE` by replacing `alice` (`members: ["bob"]`) instead of appending -> Reject.
6. **Shadow Field Injection on Household**: Member updates `/households/ROBJOKE` adding `isAdmin: true` -> Reject (`hasOnly` guard).
7. **Dish Creation by Non-Member**: `charlie` creates a dish in `/households/ROBJOKE/dishes/d1` -> Reject.
8. **Dish Immutable Field Tampering**: `bob` updates `createdBy` or `createdAt` on an existing dish created by `alice` -> Reject.
9. **Invalid Day Kind**: Member writes `kind: "feest"` (not in `['leeg', 'koken', 'afhaal', 'opwarm', 'niet_koken']`) to `/households/ROBJOKE/days/2026-10-11` -> Reject.
10. **Oversized String / Resource Poisoning**: Member writes a 5000-character dish `name` -> Reject.
11. **LooseItem Creator Spoofing**: `alice` creates a loose item with `createdBy: "bob"` -> Reject.
12. **Future Timestamp Spoofing**: Client writes `updatedAt` in the future (`> request.time`) -> Reject.
