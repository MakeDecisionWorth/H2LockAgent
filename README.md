# H2LockAgent

A universal Java agent that rewrites embedded **H2** JDBC URLs at the
`org.h2.jdbc.JdbcConnection` constructor, injecting a configurable `FILE_LOCK`
mode — without modifying the plugin that opens the connection.

It patches the constructor bytecode by type name, so it works no matter how H2 is
loaded: direct `new JdbcConnection(...)`, reflection, or an isolated plugin class
loader. Verified against **AxVaults**, **AxShulkers**, and **LuckPerms**.

## Use cases

- A Paper/Spigot plugin opens embedded H2 without setting `FILE_LOCK`, and the
  default exclusive lock (mandatory on Windows) leaves a stale `*.lock.db` after a
  crash, blocking restart — switch to `-Dh2lock.type=socket`.
- You need a second connection (e.g. a backup/inspection tool) to coordinate with
  the server instead of being hard-blocked by the OS lock — `socket` mode.
- You want to strip file locking entirely in a controlled, single-writer setup —
  `-Dh2lock.type=no`.

## Quick start

Add `-javaagent` before `-jar` in your server start command:

```sh
java -javaagent:H2LockAgent-1.0.0.jar -Dh2lock.type=socket -jar paper.jar nogui
```

With no properties the agent injects `FILE_LOCK=SOCKET;AUTO_SERVER=TRUE` (because
`h2lock.autoserver` defaults to `true`, which promotes the `fs` default to the
compatible `socket` mode). To keep H2's plain default lock and open no server,
use `-Dh2lock.autoserver=false`. Add `-Dh2lock.verbose=true` to log every
rewritten URL and confirm it is working.

## Properties

All are optional JVM system properties (`-D<name>=<value>`).

| Property | Default | Values | Description |
|---|---|---|---|
| `h2lock.enabled` | `true` | `true` / `false` | Global switch. `false` disables all patching. |
| `h2lock.type` | `fs` | `fs` / `socket` / `no` | `FILE_LOCK` mode to inject: `FS` (OS file lock), `SOCKET` (cross-process socket lock), `NO` (no locking). |
| `h2lock.autoserver` | `true` | `true` / `false` | Also inject `AUTO_SERVER=TRUE`, letting a second connection (e.g. a backup tool) open the live DB. Requires a compatible lock, so `fs` is promoted to `socket`; with `no` it is disabled. Also sets `h2.bindAddress=localhost` so the auto-server is loopback-only. |
| `h2lock.override` | `false` | `true` / `false` | If the URL already contains `FILE_LOCK`, `true` replaces it with `h2lock.type`; `false` leaves it untouched. |
| `h2lock.verbose` | `false` | `true` / `false` | Log the full rewritten URL (and skipped/untouched URLs) with the `[H2LockAgent]` tag. |

Only embedded file `jdbc:h2:` URLs are touched; in-memory (`mem:`), remote
(`tcp:`/`ssl:`) and `zip:` URLs pass through unchanged. The agent only changes
the lock/server settings — it does not affect `MODE=MySQL` or any other H2 setting.

## Live backup (AUTO_SERVER)

With `AUTO_SERVER=TRUE` a second connection can open the database while the
server holds it — the correct way to snapshot a running embedded H2, since the
live `.mv.db` file is OS-locked and cannot be copied. Connect from a helper and
run an online export (never a raw file copy):

```
jdbc:h2:./plugins/AxVaults/data;AUTO_SERVER=TRUE
-> SCRIPT TO 'plugins/AxVaults/backup/axvaults.sql'   -- or  BACKUP TO 'axvaults.zip'
```

The exported file is consistent and unlocked, so a backup plugin can zip it.

## Build

Requires JDK 17+ and Maven:

```sh
mvn clean package
```

Output: `target/H2LockAgent-1.0.0.jar`.
