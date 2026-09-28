# ChaseKeno

A standalone Java Swing Keno game with deterministic, verifiable draws.

## Requirements

- JDK 17 or newer

## Run the game

Compile the source and launch the desktop application:

```bash
javac ChaseKeno.java
java ChaseKeno
```

The game opens a Swing window. Compiled `.class` files are local build output
and are intentionally not committed.

## Run the headless self-test

```bash
javac ChaseKeno.java
java ChaseKeno test
```

The self-test exercises the provably-fair draw verification, tamper detection,
and payout-table RTP calculations.

## Project structure

- `ChaseKeno.java` — game logic, provably-fair draw implementation, self-test,
  and Swing UI
- `README.md` — local build and run instructions

This repository is a desktop Java application. It does not expose an HTTP
server and is not configured for Vercel or other web-hosting runtimes.
