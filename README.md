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

## Browser demo

The repository also includes a static browser demo:

- `index.html` — browser UI
- `styles.css` — responsive visual theme
- `app.js` — client-side Keno logic and proof verification

To preview it locally, run a static file server from the repository root:

```bash
python3 -m http.server 8080
```

Then open <http://localhost:8080>.

The same files can be deployed directly to Vercel, GitHub Pages, Netlify, or
any static hosting provider. No build step or server runtime is required.

The Java Swing application remains available as the desktop version.
