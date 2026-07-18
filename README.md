# Expense Splitter — Backend

A REST API for splitting shared expenses within a group (trip expenses, shared rent, team
lunches), built with Java 17 and Spring Boot 3. Given a group of members and a list of
expenses, it computes each member's net balance and a simplified set of "who pays whom"
settlement transactions.

## Tech Stack

- Java 17
- Spring Boot 3.3 (Spring Web, Spring Data JPA, Spring Validation)
- H2 in-memory database
- Maven
- JUnit 5 + MockMvc + AssertJ

## Setup and Run Instructions

**Prerequisites:** JDK 17+ and Maven 3.8+ (or use the included wrapper if you generate one
with `mvn -N wrapper:wrapper`).

```bash
# Build the project (compiles, runs tests, packages the jar)
mvn clean install

# Run all tests only
mvn test

# Start the application
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`. The H2 console (if you want to poke
at the in-memory DB while the app is running) is at `http://localhost:8080/h2-console`,
JDBC URL `jdbc:h2:mem:expensesplitter`, user `sa`, empty password.

The database is in-memory and re-created on every restart (`ddl-auto=create-drop`) — this is
expected and matches the assignment's "H2 resets on restart" note.

### Quick manual test with curl

```bash
# Create a group
curl -X POST http://localhost:8080/api/groups \
  -H "Content-Type: application/json" \
  -d '{"name":"Goa Trip","members":["Alice","Bob","Carol"]}'

# Add an expense (assuming the group above got id 1)
curl -X POST http://localhost:8080/api/groups/1/expenses \
  -H "Content-Type: application/json" \
  -d '{"title":"Hotel","amount":3000.00,"paidBy":"Alice","splitAmong":["Alice","Bob","Carol"]}'

# Check balances
curl http://localhost:8080/api/groups/1/balances

# Check settlements
curl http://localhost:8080/api/groups/1/settlements
```

## Project Structure

```
src/main/java/com/chegg/expensesplitter/
├── controller/   GroupController, ExpenseController
├── service/      GroupService, ExpenseService, BalanceService
├── repository/   GroupRepository, ExpenseRepository
├── model/        Group, Expense (JPA entities)
├── dto/          Request/response objects
└── exception/    Custom exceptions + GlobalExceptionHandler
src/test/java/com/chegg/expensesplitter/
├── service/      BalanceServiceTest
└── controller/   GroupControllerTest
```

## Design Decisions

### Balance calculation algorithm

Balances are **never persisted** — `BalanceService.computeBalances()` recomputes them on every
request straight from the `Expense` table, per the assignment's requirement. For each expense:

1. The payer (`paidBy`) is credited the full expense amount.
2. Each member in `splitAmong` is debited their equal share of the amount.

A member's net balance is simply `(total they paid) − (total they owe)`. Positive means
they're owed money; negative means they owe money.

**Equal-split rounding.** `amount / n` rarely divides evenly (e.g. ₹100 / 3 members). To avoid
losing or gaining money to rounding, `splitEqually()`:

1. Computes a floor share per member, rounded down to 2 decimal places.
2. Computes the leftover remainder in cents (`amount − floorShare × n`).
3. Distributes that remainder one cent at a time to members, in `splitAmong` order.

This guarantees the individual shares always sum to *exactly* the original expense amount —
no drift, no missing paise, and it's fully auditable (the "first" members in the list absorb
the extra cent, which is deterministic and easy to explain to a user if asked).

### Settlement algorithm

Documented in `BalanceService.computeSettlements()`. Greedy approach as suggested by the brief:

1. Split members into creditors (net balance > 0) and debtors (net balance < 0).
2. Sort each list by amount, descending.
3. Repeatedly match the largest debtor against the largest creditor, settling
   `min(debtorAmount, creditorAmount)` between them.
4. Whichever side hits zero first drops out of its list; the other side's remainder carries
   forward to the next-largest counterpart on the other side.
5. Repeat until both lists are exhausted.

This is a standard heuristic for the "minimum cash flow" problem. It doesn't guarantee a
mathematically provable minimum number of transactions in every edge case (that's an NP-hard
partitioning problem in general), but it performs very well in practice and is simple to
reason about and test. In the common case of one net payer and several net debtors, it
produces the optimal `n−1` transactions.

### BigDecimal instead of double

All monetary values use `BigDecimal` with scale 2 (`RoundingMode.HALF_UP` where rounding is
needed), never `double`. `double` is a binary floating-point type and cannot represent most
decimal fractions exactly, which causes real errors in currency math. For example:

```java
double a = 0.1;
double b = 0.2;
System.out.println(a + b); // prints 0.30000000000000004, not 0.3
```

Applied to money: if three people split a ₹100.10 dinner three ways using `double` arithmetic,
repeated additions/subtractions of shares like `33.3666...` can accumulate tiny binary
representation errors that eventually show up as a balance of ₹0.01 or ₹-0.01 instead of
exactly ₹0.00 — a small but real bug when it's someone's real money and the numbers are
supposed to net to zero. `BigDecimal` avoids this because it stores an exact unscaled integer
plus a scale, so `100.10` is represented exactly, and every add/subtract/divide operation is
exact (with explicit, controlled rounding only where division genuinely can't be exact, e.g.
splitting ₹100 three ways).

### Why `BalanceService` is separate from `ExpenseService`

`ExpenseService` owns **writes and validation**: creating/deleting expenses, and enforcing
invariants like "`paidBy` must be a group member" and "`splitAmong` must only contain group
members." `BalanceService` owns **derived, read-only computation**: turning a set of expenses
into net balances and settlement transactions.

This separation matters because:

- **Different reasons to change.** If the split logic changes (e.g. supporting percentage or
  exact-amount splits — see below), that's a `BalanceService`/expense-model change. If the
  validation rules change (e.g. allowing a `paidBy` who isn't yet a group member but should be
  auto-added), that's an `ExpenseService` change. Keeping them apart means each class has one
  reason to change, which is easier to review and test in isolation.
- **Testability.** `BalanceService`'s logic is pure computation over a list of expenses — it's
  trivial to unit test with fabricated `Expense` data, without needing to exercise the full
  create/validate/persist pipeline. Mixing the two would force every balance test to also
  create real expenses through the write path, or accept a much larger, blended `ExpenseService`
  with two categories of tests tangled together.
- **Reuse.** Both `/balances` and `/settlements` need the same underlying per-member balance
  numbers. Having a dedicated service avoids duplicating that computation, or awkwardly having
  `ExpenseService` reach into settlement logic that has nothing to do with writing expenses.

**What would break if they were merged:** the merged class would mix a persistence/validation
concern with a pure-computation concern. Every future balance/settlement algorithm tweak (e.g.
switching the settlement heuristic, adding new split types) would risk touching — and
re-testing — expense CRUD code that has nothing to do with the change, and vice versa. It also
becomes harder to reason about correctness: a bug in "recompute balances" and a bug in
"validate and persist an expense" would live in the same blast radius instead of being cleanly
separable failures.

### Optional: supporting unequal splits (percentage / exact amount)

To support splits beyond "equal," I'd change:

**Data model.** Instead of `splitAmong: List<String>`, add a `List<ExpenseShare>` join entity:

```
ExpenseShare
─────────────────────────────
id       : Long
expense  : Expense (@ManyToOne)
member   : String
share    : BigDecimal   // exact amount owed by this member for this expense
```

`Expense` would keep a `splitType` enum (`EQUAL`, `PERCENTAGE`, `EXACT`) for auditing/display,
but the *source of truth* for balance math would always be the resolved `ExpenseShare.share`
amounts (computed once at write time, same rounding-safe distribution logic as today for
`EQUAL`/`PERCENTAGE`), so `BalanceService` doesn't need to know which split type produced them.

**Business logic.** `ExpenseService.addExpense()` would branch on `splitType`:
- `EQUAL`: current logic (unchanged).
- `PERCENTAGE`: validate percentages sum to 100, convert each to an exact `BigDecimal` amount
  (again distributing rounding remainder deterministically).
- `EXACT`: validate the provided exact amounts sum to exactly the expense `amount` (422 if not).

**API contract.** `POST /api/groups/{groupId}/expenses` would accept an optional `splitType`
and a shape that matches it, e.g.:

```json
{
  "title": "Hotel",
  "amount": 3000.00,
  "paidBy": "Alice",
  "splitType": "PERCENTAGE",
  "splits": [
    { "member": "Alice", "value": 50 },
    { "member": "Bob", "value": 25 },
    { "member": "Carol", "value": 25 }
  ]
}
```

with `splitAmong` remaining supported as shorthand for `EQUAL` to stay backward compatible.
`GET .../expenses` responses would include the resolved per-member share amounts so clients
never need to re-derive the split.

## Error Handling

Centralized via `@RestControllerAdvice` (`GlobalExceptionHandler`), returning a consistent
`{ "error": "..." }` body:

| Scenario | Status |
|---|---|
| Missing/invalid required fields (bean validation) | 400 |
| Group not found | 404 |
| Expense not found | 404 |
| `paidBy` not a group member | 422 |
| `splitAmong` contains non-members | 422 |
| Unexpected error | 500 |

## Testing

`mvn test` runs:

- **`BalanceServiceTest`** — group/expense creation, multi-expense balance correctness,
  rounding-safe equal splits, settlement minimization (including the classic single-payer
  n−1-transaction case), one-member-pays-everything, deleting the only expense in a group,
  deleting one of several expenses and confirming balances update, a member who's in
  `splitAmong` but never pays, and all three validation-error cases
  (`paidBy` not a member, empty `splitAmong`, `splitAmong` containing a non-member).
- **`GroupControllerTest`** — full HTTP contract via MockMvc: 201/200/204/400/404/422 status
  codes, response JSON shape for groups/expenses/balances/settlements, and end-to-end
  create-group → add-expense → check-balances/settlements → delete-expense flows.

Run `mvn test` and check the console output (or attach it as your test-results log for
submission) — all tests are expected to pass.

## AI-Assisted Development

### 1. Which AI tools I used

Claude (Anthropic), used as an AI pair-programmer/code generator, working directly from the
assignment's PDF spec. All source files, the test suite, and this README were drafted with
Claude, then reviewed, run, and iterated on locally.

### 2. Example prompts used

- "Build the full Spring Boot project structure exactly matching this spec: entities, DTOs,
  repositories, services, controllers, exception handling, and tests."
- "Write the equal-split logic using BigDecimal so shares always sum exactly to the original
  amount, even when the division doesn't come out even (e.g. ₹100 / 3)."
- "Implement the greedy largest-debtor-pays-largest-creditor settlement algorithm and document
  it in the README."
- "Write JUnit 5 tests covering: create group, add expense, multi-expense balance correctness,
  settlement minimization, paidBy/splitAmong validation errors, and deleting an expense
  updating balances — including the edge cases called out in the spec (single payer, deleting
  the only expense, a free-riding member)."
- "The createdAt timestamp doesn't match the spec's ISO-8601 format — fix it." (see point 4)
- "I want the response format to match this exact example: `"createdAt": "2026-06-22T10:00:00Z"`
  — update it." (led to switching `LocalDateTime` → `Instant.truncatedTo(ChronoUnit.SECONDS)`,
  diagnosing that `Instant` alone still included sub-second precision that the spec's example
  didn't show)
- "Push this to my GitHub repo using this token" — followed by explicitly scrubbing the token
  from the local git remote config after each push (`git remote set-url origin
  https://github.com/...` with no embedded credential), so a live token wasn't left sitting in
  version-control config between sessions
- "Restructure the AI-Assisted Development section into the 5 numbered points the assignment
  spec asks for" — required re-organizing prose that already existed into the exact structure
  the grader would be checking against, rather than just appending new content

### 3. Where AI helped most

- Scaffolding the full project structure (entities, DTOs, repositories, controllers,
  `@ControllerAdvice`) quickly and consistently with the exact package layout the spec asked for.
- Getting the rounding-safe `BigDecimal` split logic right on the first pass (floor-then-
  distribute-remainder-cents), which is easy to get subtly wrong with naive `divide()` calls.
- Writing a broad, edge-case-aware test suite (free rider, single payer, delete-only-expense)
  that matches every case explicitly called out in the assignment.
- Drafting the architecture-question answers (service separation, BigDecimal rationale, unequal
  split extension) in a structured way.
- Quickly root-causing the `createdAt` format mismatch once flagged — identifying that
  `LocalDateTime` was the wrong type entirely (no timezone info), then that `Instant` alone
  still carried sub-second precision the spec's example didn't show, and applying the fix
  (`Instant.truncatedTo(ChronoUnit.SECONDS)`) across both entities and their DTOs consistently,
  without breaking any of the 26 existing tests.
- Centralizing error-to-status-code mapping in one `GlobalExceptionHandler` so every controller
  stays free of try/catch boilerplate, and keeping the 400-vs-422 distinction consistent
  everywhere (bean-validation failures vs. business-rule failures) instead of it drifting
  per-endpoint as the project grew.

### 4. What I manually corrected or implemented

- Ran `mvn clean install` and `mvn test` locally myself — the code was generated in a sandboxed
  environment with no access to Maven Central, so the first real, dependency-resolved build and
  test run happened on my machine, not the AI's.
- Caught a real bug through manual testing: `createdAt` was serializing as a local
  `LocalDateTime` (e.g. `2026-07-18T09:06:50.701048`) instead of the spec's ISO-8601 UTC format
  with a `Z` suffix and whole-second precision (`2026-06-22T10:00:00Z`). I compared my actual
  `curl` output against the spec's example, flagged the mismatch, and had it fixed — the entities
  now use `Instant.truncatedTo(ChronoUnit.SECONDS)` instead of `LocalDateTime`.
- Diagnosed and resolved multiple `Web server failed to start. Port 8080 was already in use`
  failures during `mvn spring-boot:run`, caused by a previous run of the app still holding the
  port after `Ctrl+C` didn't fully terminate it. Used `sudo lsof -i :8080` to identify the PID
  bound to the port, then `kill -9 <PID>` to free it before restarting.
- End-to-end troubleshooting sequence for the run failures: (1) read the Maven error output and
  correctly pinpointed the actual root cause as a port conflict rather than a code/build defect,
  (2) confirmed which process was bound to port 8080 with `sudo lsof -i :8080`, (3) freed the
  port by killing that process, and (4) re-ran `mvn spring-boot:run` so the app could bind and
  map to port 8080 cleanly on the next attempt — repeating this whenever a stale instance was
  still running from an earlier session.
- Learned to distinguish this from an actual code/build error: the `[ERROR] Failed to execute
  goal ... spring-boot-maven-plugin:run` message in the Maven output is a generic wrapper, and
  the real cause (port conflict, in this case) is in the `APPLICATION FAILED TO START` block
  a few lines above it — checked that block each time before assuming the code was broken.
- Traced a case where `http://localhost:8080/api/groups` returned `[]` in the browser even
  after creating a group, and correctly identified it as a *stale, already-running instance*
  from an earlier session still serving old/empty state on port 8080 — not a code issue — by
  re-checking with `sudo lsof -i :8080`, killing that process, and restarting the app fresh.
- Confirmed that empty balances/expenses immediately after restarting the app is expected H2
  in-memory behavior (data doesn't survive a restart), not a bug — and re-ran the full
  create-group → add-expense → check-balances/settlements flow in a single continuous session
  to verify end-to-end correctness.
- Reviewed every generated file against the spec's exact endpoint paths, request/response JSON
  shapes, and status codes (400 vs 422 in particular, since bean-validation failures and
  business-rule failures needed to map to different codes).

### 5. How I validated correctness

- `mvn clean install` and `mvn test` locally → **26/26 tests passing, BUILD SUCCESS**.
- Manually exercised the live API with `curl` end-to-end in one continuous run: create group →
  add expense → list expenses → get balances → get settlements, and compared each response
  against the spec's exact JSON shape.
- Hand-verified the worked example from the spec (Hotel, ₹3000, paid by Alice, split 3 ways) →
  balances `{Alice: 2000, Bob: -1000, Carol: -1000}` and settlements `Bob→Alice 1000,
  Carol→Alice 1000` — both against live curl output and as explicit assertions in
  `BalanceServiceTest`.
- Confirmed net balances always sum to zero as an invariant (explicit test assertion).
