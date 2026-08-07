# Budget Guardian

**A personal finance desktop application built as a data structures & algorithms showcase.**

Budget Guardian looks like a finance tracker. Under the hood, every core
feature is deliberately powered by a **custom, from-scratch implementation**
of a classic data structure or algorithm — no `java.util` collections
(`ArrayList`, `HashMap`, `LinkedList`, `Stack`, `Queue`, `PriorityQueue`, ...)
are used anywhere in business logic. An automated architecture-guard test
enforces this rule on every build.

Built for a Data Structures course project, portfolio piece, and GitHub
showcase — not a CRUD app with a database.

---

## Table of Contents

- [Why This Project Exists](#why-this-project-exists)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Custom Data Structures](#custom-data-structures)
- [Custom Algorithms](#custom-algorithms)
- [Features](#features)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Testing](#testing)
- [Packaging](#packaging)
- [Design Patterns Used](#design-patterns-used)
- [Key Design Decisions](#key-design-decisions)
- [License](#license)

---

## Why This Project Exists

Most "finance tracker" projects lean entirely on `ArrayList` and `HashMap`
and call it a day. Budget Guardian inverts that: the finance domain is the
*application* of the data structures course, not the point of it.

Every structure was chosen because a real feature needed its specific
performance characteristic:

| Need | Structure | Why |
|---|---|---|
| O(1) undo push/pop | `Stack<Action>` | LIFO history, unbounded, no resize cost |
| O(1) FIFO reminders | `Queue<Notification>` | delivery order matters |
| "Show only the worst alert" | `PriorityQueue<Notification>` | binary max-heap, O(log n) insert, O(1) peek |
| Fast account/category/debt lookup | `HashMap<K,V>` | average O(1), separate chaining |
| Chronological ledger + reverse ("recent first") views | `DoublyLinkedList<Transaction>` | O(1) both ends, cheap reverse iteration |
| Fixed-size "last 20 transactions" widget | `CircularBuffer<Transaction>` | O(1) add, automatic oldest-eviction, constant memory |
| Chart/report data with random access | `DynamicArray<T>` | O(1) get, amortized O(1) append |
| Money-transfer network + reachability | `Graph<String>` (adjacency list) | DFS/BFS traversal, weighted directed multigraph |

Structures also **compose**: `PriorityQueue` is a binary heap built on top of
`DynamicArray`; `Graph` is built from `HashMap` + `DoublyLinkedList` +
`Stack` + `Queue`. The codebase is a working demonstration that these
primitives are not academic exercises — they are the actual foundation of a
real application.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven |
| GUI | JavaFX 21 (dark, Material-inspired theme, no FXML — code-built views) |
| Persistence | Pluggable: SQLite via JDBC (`sqlite-jdbc`, local mode) **or** REST backend (api mode) |
| Backend (api mode) | Node.js + Express 5 + Prisma → Neon PostgreSQL (`backend/`) |
| HTTP / JSON (api mode) | `java.net.http.HttpClient` + Gson |
| Testing | JUnit 5 |
| Documentation | Javadoc on every public type and method |
| Packaging | `jpackage` (native installer) + Maven Shade (fat jar) |

---

## Architecture

Strict one-directional layering. The UI never touches storage, and storage is
never queried at runtime after startup — it exists purely for persistence.

```
Storage (persistence only): SQLite file  ─or─  REST backend → Prisma → Neon PostgreSQL
        │  hydrated once at startup (StartupLoader)
        ▼
Repository interfaces        (repository/sqlite = JDBC, repository/api = HTTP+JSON;
                              selected once at startup via config.properties)
        │  returns/accepts custom structures exclusively
        ▼
Custom Data Structures       (DynamicArray, DoublyLinkedList, HashMap,
                               Stack, Queue, PriorityQueue, CircularBuffer,
                               Graph — owned by DataStore)
        │
        ▼
Service layer                (business logic, validation, undo, rules,
                               notifications, reports — all reads/writes
                               go through TransactionRunner for atomicity)
        │  EventBus (Observer pattern) — publish/subscribe, no direct refs
        ▼
JavaFX UI                    (controllers/views read DataStore, call
                               services, subscribe to EventBus events)
```

**Write path for every mutation** (add transaction, transfer, debt payment,
refill confirmation, ...):

1. Validate input in the service.
2. Persist via the repository interfaces (`TransactionRunner`) — storage-first,
   so a mid-operation crash or a failed remote save never leaves memory ahead
   of what was persisted. In local mode this is one SQLite transaction; in api
   mode each REST endpoint is atomic server-side.
3. Mutate the in-memory `DataStore` structures.
4. Push the inverse `Action` onto the undo `Stack`.
5. Re-run the rule engine (danger spending / budget / debt / refill checks).
6. Publish an `EventType` on the `EventBus` — subscribed views refresh.

### Cloud persistence (api mode)

The desktop can persist through a REST backend instead of the local SQLite
file — same services, same data structures, same algorithms; only the
repository implementation changes. The desktop never connects to PostgreSQL
and never holds database credentials.

```
Java Desktop → Repository layer → HTTP REST API → Node.js (Express) → Prisma → Neon PostgreSQL
```

- Switch modes in `config.properties` (`storage.mode=local|api`) — see
  [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
- Endpoint reference: [docs/API.md](docs/API.md).
- Sequence diagrams, error handling, consistency model, ADRs:
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### JavaFX collection interop (the one documented exception)

JavaFX's `TableView`, `BarChart`, etc. require `ObservableList`. This is
framework interop **at the UI boundary only** — an `ObservableList` is built
by copying from a custom structure just before rendering, and it is never
used as a data store. Business logic, repositories, and the data structures
package remain 100% free of `java.util` collections; this is enforced by
`ArchitectureGuardTest`, which scans source files and fails the build if any
`datastructures`, `model`, `repository`, `service`, or `algorithm` file
imports a forbidden `java.util` collection type.

---

## Custom Data Structures

All in `com.budgetguardian.datastructures`. Each class ships full Javadoc
covering purpose, design rationale, advantages/trade-offs, and Big-O for
every operation.

| Class | Backing | Used for |
|---|---|---|
| `DynamicArray<T>` | resizable `Object[]`, 2× grow / 2× shrink hysteresis | Report rows, chart series, the heap array inside `PriorityQueue` |
| `DoublyLinkedList<T>` | doubly linked nodes, head/tail refs | Transaction ledger, transfer history, debt payment history, `Graph` adjacency chains |
| `HashMap<K,V>` | separate-chaining hash table, 0.75 load factor, bit-spread hash | Accounts, categories, debts, refill items, settings, derived totals |
| `Stack<T>` | singly linked nodes | Undo action history |
| `Queue<T>` | singly linked nodes, head+tail refs | FIFO reminder delivery |
| `PriorityQueue<T>` | binary max-heap over `DynamicArray` | Hero alert banner (only the highest-priority notification is shown) |
| `CircularBuffer<T>` | fixed-size ring array | "Recent 20 transactions" dashboard widget |
| `Graph<T>` | `HashMap<T, DoublyLinkedList<Edge<T>>>` adjacency list | Account transfer network, DFS/BFS visualization |

Shared contracts (`Iterator<T>`, `Iterable<T>`, `Comparator<T>`) replace their
`java.util`/`java.lang` namesakes so the whole package has zero dependency on
the Collections Framework.

---

## Custom Algorithms

`com.budgetguardian.algorithm`, Strategy pattern (`SortStrategy<T>` /
`SearchStrategy<T>`), each with documented complexity:

| Algorithm | Complexity | Used for |
|---|---|---|
| **Merge Sort** | O(n log n), **stable** | *(available; demonstrates stable ordering)* |
| **Quick Sort** | O(n log n) avg, median-of-three pivot | *(available; in-place general sort)* |
| **Heap Sort** | O(n log n), in-place | Category-total ranking in `ReportService` ("highest spending category") |
| **Linear Search** | O(n) | Free-text transaction search (`SearchService`) |
| **Binary Search** | O(log n) | Lookups on sorted report data |

---

## Features

- **Dashboard** — today's spend vs. daily budget (progress bar), account
  balances, monthly stats (total / average / highest day), category totals,
  recent-20 transactions, hero alert banner.
- **Calendar** — month grid, per-day spend, color-coded against the daily
  budget.
- **Transactions** — add / edit / delete expense, income, or withdrawal;
  Ctrl+N to add, refill-duplicate detection prompt on repeat item purchases.
- **Transfers** — move money between accounts without ever counting as
  income or expense.
- **Debt** — track money owed and money you're owed, with **partial
  payments** that auto-settle the debt once fully paid.
- **Refillable items** — detects repeat purchases of the same item and asks
  to remember it; tracks a running-average purchase interval and flags
  overdue items.
- **Danger spending guard** — Alcohol + Gambling combined weekly (Mon–Sun)
  limit, highest-priority alert when breached.
- **Reports** — weekly / monthly / yearly summaries, category ranking (heap
  sort), highest-spending category, danger-spending report, debt report,
  refill report, bar chart.
- **Transfer graph visualization** — DFS/BFS traversal over the account
  network, drawn with directed weighted edges.
- **Search (Ctrl+F)** — live free-text search across reason, item, account,
  and category.
- **Undo (Ctrl+Z)** — every mutation is undoable via a Command-pattern
  action stack; no redo, by design.
- **Notifications** — priority-ranked hero banner, FIFO reminder queue, and
  a recent-alert history, all reachable from a bell icon in the toolbar.
- **Four fixed accounts**: Saving, Scholarship, SCB, TrueMoney.
- **Eleven spending categories**, two of which (Alcohol, Gambling) are
  flagged "danger."

---

## Project Structure

```
src/main/java/com/budgetguardian/
├── app/              Main (JavaFX Application) + Launcher (fat-jar entry point)
├── controller/        (reserved — current UI logic lives in view/ controllers-as-views)
├── model/             Immutable domain records: Account, Transaction, Transfer, Debt, ...
├── repository/        Storage-neutral interfaces + Repositories bundle + StorageException
│   ├── sqlite/          JDBC implementations (local mode)
│   └── api/             REST implementations (api mode)
├── dto/               Plain wire carriers for the REST backend (Gson-friendly)
├── mapper/            DTO ↔ domain-record conversion (the only place they meet)
├── network/           HttpJsonClient — timeouts, idempotent-only retry, X-API-Key
├── service/           Business logic, DataStore, EventBus, undo, rules, notifications
├── view/              JavaFX views, dialogs, and the AppShell (code-built, no FXML)
├── datastructures/    DynamicArray, DoublyLinkedList, HashMap, Stack, Queue,
│                      PriorityQueue, CircularBuffer, Graph, Iterator/Iterable/Comparator
├── algorithm/
│   ├── sorting/        MergeSort, QuickSort, HeapSort, SortStrategy
│   └── searching/       LinearSearch, BinarySearch, SearchStrategy
├── util/               Money (satang <-> THB formatting/parsing)
└── database/           DatabaseManager (schema bootstrap, connection lifecycle)

src/main/resources/
├── css/styles.css      Dark Material-inspired theme
├── db/schema.sql       Idempotent SQLite schema + seed data
└── config.properties   Annotated config template (copied to the user dir on first run)

backend/                Node.js REST backend (api mode)
├── prisma/             schema.prisma, migrations/, seed.js
└── src/                routes → zod validation → controllers → services (Prisma)

docs/                   ARCHITECTURE.md (diagrams + ADRs), API.md, DEPLOYMENT.md

src/test/java/com/budgetguardian/
├── datastructures/     Structure-by-structure exhaustive test suites
├── algorithm/           Sort/search correctness + stability tests
├── repository/          JDBC round-trip tests (temp-file SQLite)
├── service/              Business-logic, undo, rule-engine, reminder tests
├── integration/          Full-stack session test + architecture guard
└── util/                 Money formatting/parsing tests
```

---

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+

### Run

```bash
mvn clean javafx:run
```

On first run, `budget.db` is created in the per-user data directory and
seeded with four accounts, eleven categories, and default settings (180 THB
daily budget, 200 THB danger weekly limit, 20:00 reminder time). An
annotated `config.properties` is created next to it.

### Run against the cloud (api mode)

```bash
# 1. one-time backend setup — see docs/DEPLOYMENT.md for Neon details
cd backend && npm install && cp .env.example .env   # fill in the database URLs
npm run db:migrate && npm run db:seed && npm run dev

# 2. point the desktop at it: edit config.properties in the user data dir
#    storage.mode=api
#    api.baseUrl=http://localhost:8080/api/v1

mvn clean javafx:run
```

### Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+N` | New transaction |
| `Ctrl+Z` | Undo last action |
| `Ctrl+F` | Jump to search |

---

## Testing

```bash
mvn test
```

259 JUnit 5 tests, organized by layer:

- **Data structures** — exhaustive per-structure suites: empty/single/many,
  resize/rehash boundaries, iterator order (forward + reverse), fail-fast
  behavior, null rejection.
- **Algorithms** — sorted/reverse/random/duplicate/empty inputs, stability
  verification for merge sort, binary-search hit/miss/bounds.
- **Repositories** — round-trip persistence against temp-file SQLite,
  foreign-key enforcement, transaction rollback.
- **Services** — business rules, undo round-trips (add → undo → identical
  state), transfer atomicity, debt partial-payment auto-settle + undo
  reopen, refill detection logic, danger-week Mon→Sun boundary.
- **REST layer** — DTO↔model mapper round-trips, `HttpJsonClient` behavior
  (error shapes, API key, idempotent-only retry) against a stub HTTP server,
  and the api-mode repositories hydrating the custom structures.
- **Integration** — a realistic multi-step session driven through the full
  object graph; an end-to-end **synchronization test** running the real
  service layer over the REST repositories (startup hydration, remote-first
  writes, undo with original-id restore, memory-intact-on-failure, restart
  consistency); plus an **architecture-guard test** that scans the source
  tree and fails the build if any business-logic file imports a forbidden
  `java.util` collection.

---

## Packaging

`mvn package` produces a self-contained fat jar
(`target/budget-guardian-<version>-app.jar`) runnable with `java -jar`.

To build a native installer with `jpackage` (bundled with JDK 21):

```bash
# Windows — MSI installer, or app-image for a portable folder
pwsh scripts/package-windows.ps1
pwsh scripts/package-windows.ps1 -Type app-image

# macOS / Linux
./scripts/package-unix.sh
./scripts/package-unix.sh app-image
```

Installers are written to `target/installer/`.

---

## Design Patterns Used

| Pattern | Where |
|---|---|
| **Repository** | Interface per aggregate; interchangeable SQLite (JDBC) and REST implementations |
| **DTO + Mapper** | `dto/` wire carriers, `mapper/` conversions — the UI never sees a DTO, the wire never sees a domain record |
| **Service Layer** | All business logic, validation, and orchestration |
| **MVC** (adapted) | `view/` = controller + view combined; `model/` = domain records; `service/` = the rest of "controller" logic |
| **Observer** | `EventBus` — services publish, views subscribe, zero direct coupling |
| **Command** (+ Memento) | `Action` sealed interface — every mutation is an undoable, snapshot-carrying command |
| **Strategy** | `SortStrategy<T>` / `SearchStrategy<T>` — algorithm choice injected, not hardcoded |
| **Factory-ish composition** | `ServiceContext` — single composition root wiring repositories → services → rule engine |

---

## Key Design Decisions

- **Money as `long` satang** (1 THB = 100 satang), never floating point —
  exact arithmetic, no rounding drift.
- **Transfers are never expenses.** A dedicated `Transfer` model/table/service
  keeps balance-only moves structurally separate from the spending ledger.
- **Undo, no redo.** Session-scoped `Stack<Action>`; simpler mental model,
  matches the original requirement.
- **Refill "No" is not remembered.** Declining a duplicate-purchase prompt
  stores nothing, so the next repeat purchase asks again — only "Yes" persists.
- **Danger week = calendar Monday–Sunday**, not a rolling 7 days — cheaper
  to compute, easier to reason about.
- **Storage-first writes.** Every mutation persists (SQLite transaction or
  REST call) before touching in-memory state, so a crash or a failed remote
  save never desyncs memory from what was actually stored.
- **The desktop never sees the database.** In api mode all persistence goes
  through the Node backend; Neon credentials exist only in
  `backend/.env`. Numeric ids were kept over UUIDs to leave the domain
  records untouched — see ADR-2 in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## License

Educational / portfolio project. No license file included; add one if you
intend to distribute.
