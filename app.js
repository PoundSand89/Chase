const TOTAL_TILES = 40;
const DRAW_COUNT = 10;
const FEE_PERCENT = 5;
const MAX_MULTIPLIER = 1000;

const TABLES = {
  Medium: [
    [0.4, 2.75], [0, 1.8, 5.1], [0, 0, 2.8, 50], [0, 0, 1.7, 10, 100],
    [0, 0, 1.4, 4, 14, 390], [0, 0, 0, 3, 9, 180, 710],
    [0, 0, 0, 2, 7, 30, 400, 800], [0, 0, 0, 2, 4, 11, 67, 400, 900],
    [0, 0, 0, 2, 2.5, 5, 15, 100, 500, 1000], [0, 0, 0, 1.6, 2, 4, 7, 26, 100, 500, 1000]
  ],
  Classic: [
    [0, 3.96], [0, 1.9, 4.5], [0, 1, 3.1, 10.4], [0, 0.8, 1.8, 5, 22.5],
    [0, 0.65, 1.14, 3.54, 12.03, 13.46], [0, 0, 1, 3.68, 7, 16.5, 40],
    [0, 0, 0.82, 2.4, 4.49, 12.05, 25.33, 44.94], [0, 0, 0, 2.2, 4, 13, 22, 55, 70],
    [0, 0, 0, 1.64, 2.85, 7.98, 13.67, 32.82, 54.7, 72.93],
    [0, 0, 0, 1.4, 2.25, 4.5, 8, 17, 50, 80, 100]
  ],
  High: [
    [0, 3.96], [0, 0, 17.1], [0, 0, 0, 81.5], [0, 0, 0, 10, 259],
    [0, 0, 0, 4.5, 48, 450], [0, 0, 0, 0, 11, 350, 710],
    [0, 0, 0, 0, 4.88, 112.84, 298.88, 365.98], [0, 0, 0, 0, 5, 20, 270, 600, 900],
    [0, 0, 0, 0, 3.17, 10.45, 105.59, 247.37, 522.35, 596.97],
    [0, 0, 0, 0, 3.5, 8, 13, 63, 500, 800, 1000]
  ]
};

const state = {
  balance: 1000,
  profit: 0,
  nonce: 0,
  picks: new Set(),
  drawn: new Set(),
  serverSeed: randomSeed(),
  lastProof: null,
  running: false
};

const $ = (id) => document.getElementById(id);
const money = (value) => Math.round((value + Number.EPSILON) * 100) / 100;
const fee = (bet) => money(bet * FEE_PERCENT / 100);
const totalCost = (bet) => money(bet + fee(bet));

function randomSeed() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function mulberry32(seed) {
  return () => {
    let value = seed += 0x6D2B79F5;
    value = Math.imul(value ^ value >>> 15, value | 1);
    value ^= value + Math.imul(value ^ value >>> 7, value | 61);
    return ((value ^ value >>> 14) >>> 0) / 4294967296;
  };
}

async function drawRound(
  nonce = state.nonce,
  clientSeed = $("client-seed").value,
  selectedPicks = state.picks
) {
  const picks = [...selectedPicks].sort((a, b) => a - b);
  const seedHash = await sha256(`${state.serverSeed}:${clientSeed}:${nonce}`);
  const picksHash = await sha256(`[${picks.join(", ")}]`);
  const gameInput = `${seedHash}:${picksHash}`;
  const seed = Number.parseInt(gameInput.slice(0, 8), 16) >>> 0;
  const random = mulberry32(seed);
  const tiles = Array.from({ length: TOTAL_TILES }, (_, index) => index + 1);
  for (let index = tiles.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1));
    [tiles[index], tiles[swap]] = [tiles[swap], tiles[index]];
  }
  const drawn = tiles.slice(0, DRAW_COUNT).sort((a, b) => a - b);
  return { seedHash, gameInput, picks, drawn, verificationHash: await sha256(`${gameInput}${drawn}${picks}${TOTAL_TILES}${DRAW_COUNT}`) };
}

function betAmount() {
  const amount = Number.parseFloat($("amount").value);
  const multiplier = Number.parseInt($("multiplier").value, 10);
  return money((Number.isFinite(amount) ? amount : 0) * multiplier);
}

function render() {
  $("balance").textContent = `$${state.balance.toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
  $("profit").textContent = `Profit $${state.profit.toFixed(2)}`;
  $("nonce").textContent = state.nonce;
  $("seed-hash").textContent = state.lastProof?.seedHash || "—";
  $("revealed-seed").textContent = state.lastProof?.serverSeed || "—";
  $("verify-hash").textContent = state.lastProof?.verificationHash || "—";
  document.querySelectorAll(".tile").forEach((tile) => {
    const value = Number(tile.dataset.value);
    tile.classList.toggle("selected", state.picks.has(value));
    tile.classList.toggle("drawn", state.drawn.has(value));
  });
  document.querySelectorAll(".spot-buttons button").forEach((button) => {
    button.classList.toggle("active", Number(button.dataset.spots) === state.pickTarget);
  });
  renderPayouts();
}

function renderPayouts() {
  const spots = state.pickTarget || 10;
  const values = TABLES[$("risk").value][spots - 1] || [];
  $("payouts").innerHTML = `<strong>Catch</strong>${values.map((value, index) => `<span>${index}: ${value ? `${value}x` : "—"}</span>`).join("")}`;
}

function setStatus(message, type = "") {
  $("status").textContent = message;
  $("status").className = `status-message ${type}`;
}

async function playOne() {
  const bet = betAmount();
  if (bet <= 0) return setStatus("Enter a positive bet.", "lose");
  if (totalCost(bet) > state.balance) return setStatus("Insufficient balance for bet and fee.", "lose");
  if (!state.picks.size) return setStatus("Pick at least one tile.", "lose");
  const proof = await drawRound(
    state.lastProof.nonce,
    state.lastProof.clientSeed,
    new Set(state.lastProof.picks)
  );
  const table = TABLES[$("risk").value][state.picks.size - 1] || [];
  const matches = proof.picks.filter((pick) => proof.drawn.includes(pick)).length;
  const multiplier = table[matches] || 0;
  const payout = Math.min(money(bet * multiplier), money(bet * MAX_MULTIPLIER));
  const net = money(payout - totalCost(bet));
  state.balance = money(state.balance - totalCost(bet) + payout);
  state.profit = money(state.profit + net);
  state.drawn = new Set(proof.drawn);
  state.lastProof = { ...proof, serverSeed: state.serverSeed, clientSeed: $("client-seed").value, nonce: state.nonce };
  state.nonce += 1;
  setStatus(`${payout > 0 ? "WIN" : "LOSE"} ${matches}/${state.picks.size} · net $${net.toFixed(2)}`, payout > 0 ? "win" : "lose");
  render();
}

async function play() {
  if (state.running) return;
  const games = Math.min(1000, Math.max(1, Number.parseInt($("games").value, 10) || 1));
  state.running = true;
  $("draw").disabled = true;
  for (let game = 0; game < games; game += 1) {
    await playOne();
    if (totalCost(betAmount()) > state.balance) break;
    if (game < games - 1) await new Promise((resolve) => setTimeout(resolve, 120));
  }
  state.running = false;
  $("draw").disabled = false;
}

async function verify() {
  if (!state.lastProof) return setStatus("No round has been played yet.", "lose");
  const proof = await drawRound();
  const valid = proof.verificationHash === state.lastProof.verificationHash
    && proof.drawn.join(",") === state.lastProof.drawn.join(",");
  $("proof-status").textContent = valid ? "Verified" : "Failed";
  $("proof-status").classList.toggle("success", valid);
  setStatus(valid ? "The last draw is verified." : "Verification failed.", valid ? "win" : "lose");
}

function buildBoard() {
  $("board").innerHTML = Array.from({ length: TOTAL_TILES }, (_, index) =>
    `<button class="tile" data-value="${index + 1}" aria-label="Choose ${index + 1}">${index + 1}</button>`).join("");
  document.querySelectorAll(".tile").forEach((tile) => tile.addEventListener("click", () => {
    const value = Number(tile.dataset.value);
    const spots = state.pickTarget || 10;
    if (state.picks.has(value)) state.picks.delete(value);
    else if (state.picks.size < spots) state.picks.add(value);
    else return setStatus(`You can select up to ${spots} tiles.`, "lose");
    state.drawn.clear();
    render();
  }));
}

function buildSpotButtons() {
  $("spot-buttons").innerHTML = Array.from({ length: 10 }, (_, index) =>
    `<button data-spots="${index + 1}">${index + 1}</button>`).join("");
  document.querySelectorAll(".spot-buttons button").forEach((button) => button.addEventListener("click", () => {
    state.pickTarget = Number(button.dataset.spots);
    state.picks.clear();
    state.drawn.clear();
    render();
  }));
}

function bindControls() {
  $("risk").addEventListener("change", render);
  $("draw").addEventListener("click", play);
  $("verify").addEventListener("click", verify);
  $("clear").addEventListener("click", () => { state.picks.clear(); state.drawn.clear(); setStatus("Board cleared."); render(); });
  $("random-pick").addEventListener("click", () => {
    state.picks = new Set(Array.from({ length: 40 }, (_, index) => index + 1).sort(() => Math.random() - .5).slice(0, state.pickTarget || 10));
    state.drawn.clear();
    render();
  });
  $("max-bet").addEventListener("click", () => {
    const multiplier = Number.parseInt($("multiplier").value, 10);
    $("amount").value = (state.balance / (multiplier * 1.05)).toFixed(2);
  });
}

state.pickTarget = 10;
buildBoard();
buildSpotButtons();
bindControls();
render();
