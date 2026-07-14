# Budget Guardian

A personal finance desktop application built as a **data structures showcase**.
Every core feature is intentionally powered by a custom implementation of a
classic data structure or algorithm — no `java.util` collections in business logic.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven |
| GUI | JavaFX (dark Material-inspired theme) |
| Persistence | SQLite via JDBC |
| Testing | JUnit 5 |
| Packaging | jpackage |

## Architecture

```
SQLite → Repository → Custom Data Structures → Service Layer → JavaFX UI
```

The UI never touches the database. All business logic operates on custom
in-memory structures; SQLite is persistence only.

## Custom Data Structures

| Structure | Used for |
|---|---|
| `DynamicArray<T>` | Dashboard aggregates, chart data, reports |
| `DoublyLinkedList<T>` | Transactions, debt and transfer history |
| `HashMap<K,V>` | Accounts, category totals, refill items, debts, settings |
| `Stack<T>` | Undo (action history) |
| `Queue<T>` | Notification and reminder queues |
| `PriorityQueue<T>` | Hero alert banner (highest-priority notification) |
| `CircularBuffer<T>` | Recent 20 transactions widget |
| `Graph` | Account transfer network with DFS/BFS visualization |

## Algorithms

Merge sort, quick sort, heap sort, linear search, binary search, DFS, BFS —
selected per use case via the Strategy pattern, with documented complexity.

## Features

- Dashboard: daily budget progress, monthly stats, category totals, hero alerts
- Calendar view with per-day spending
- Four accounts: Saving, Scholarship, SCB, TrueMoney
- Transfers between accounts (balance-only, never expenses)
- Debt tracking (pay / receive)
- Refillable item detection with purchase-interval reminders
- Danger-spending guard (alcohol + gambling weekly limit)
- Undo for every modification (Ctrl+Z)
- Fast search (Ctrl+F), new transaction (Ctrl+N)
- Weekly / monthly / yearly reports

## Build & Run

```bash
mvn clean javafx:run
```

## Test

```bash
mvn test
```

231 JUnit 5 tests cover the data structures, algorithms, repositories, services,
undo, rules, reports and a full-stack integration flow. An architecture-guard
test fails the build if any business-logic package imports a `java.util`
collection.

## Package

`mvn package` produces a self-contained fat jar
(`target/budget-guardian-<version>-app.jar`) that runs with `java -jar`.

To build a native installer with `jpackage` (bundled with JDK 21):

```bash
# Windows (MSI installer, or app-image for a portable folder)
pwsh scripts/package-windows.ps1
pwsh scripts/package-windows.ps1 -Type app-image

# macOS / Linux
./scripts/package-unix.sh
./scripts/package-unix.sh app-image
```

Installers are written to `target/installer/`.
