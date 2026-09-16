"use strict";

const form = document.querySelector("#ticket");
const fields = document.querySelector("#ticket-fields");
const message = document.querySelector("#message");
const refreshButton = document.querySelector("#refresh");
const currency = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
let counterparties = [];
let bonds = [];

async function request(url, options) {
  const response = await fetch(url, options);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.message || data.error || `Request failed (${response.status})`);
  }
  return data;
}

function fillSelect(selector, items, label) {
  const select = document.querySelector(selector);
  const selected = select.value;
  select.replaceChildren(...items.map((item) => new Option(label(item), String(item.id))));
  if (items.some((item) => String(item.id) === selected)) select.value = selected;
}

function showBalances() {
  const counterparty = counterparties.find((item) => String(item.id) === form.elements.counterpartyId.value);
  const bond = bonds.find((item) => String(item.id) === form.elements.bondId.value);
  document.querySelector("#credit").textContent = counterparty ? currency.format(counterparty.availableCredit) : "—";
  document.querySelector("#inventory").textContent = bond ? currency.format(bond.availableNotional) : "—";
  document.querySelector("#counterparty-name").textContent = counterparty ? counterparty.name : "No counterparties";
  document.querySelector("#bond-name").textContent = bond ? bond.isin : "No bonds";
}

async function loadDesk() {
  const [newCounterparties, newBonds, trades] = await Promise.all([
    request("/counterparties"), request("/bonds"), request("/rfqs"),
  ]);
  counterparties = newCounterparties;
  bonds = newBonds;
  fillSelect("#counterparty", counterparties, (item) => item.name);
  fillSelect("#bond", bonds, (item) => `${item.isin} · ${item.issuer}`);
  showBalances();
  document.querySelector("#trade-count").textContent = String(trades.length);
  document.querySelector("#trades").replaceChildren(...trades.slice().reverse().map((trade) => {
    const row = document.createElement("tr");
    for (const value of [`#${trade.id}`, trade.side, currency.format(trade.notionalAmount),
      currency.format(trade.executionPrice), trade.status]) {
      const cell = document.createElement("td");
      cell.textContent = value;
      row.append(cell);
    }
    return row;
  }));
}

function ticket() {
  return Object.fromEntries(new FormData(form));
}

function setBusy(busy) {
  fields.disabled = busy || !counterparties.length || !bonds.length;
  refreshButton.disabled = busy;
}

function clearPreview() {
  document.querySelector("#preview-status").textContent = "Ready to preview";
  document.querySelector("#preview-detail").textContent = "Submit a preview to check current credit and inventory.";
  document.querySelector("#preview-reasons").replaceChildren();
  document.querySelector("#preview-balances").hidden = true;
}

form.addEventListener("input", clearPreview);
form.addEventListener("change", showBalances);
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const body = JSON.stringify(ticket());
  clearPreview();
  setBusy(true);
  message.textContent = "Executing RFQ…";
  try {
    const trade = await request("/rfqs", {
      method: "POST", headers: { "Content-Type": "application/json" }, body,
    });
    message.textContent = `RFQ #${trade.id} executed.`;
    try {
      await loadDesk();
    } catch {
      message.textContent += " Refresh failed. Refresh the desk before submitting another trade.";
    }
  } catch (error) {
    message.textContent = `${error.message}. Check the desk before retrying if the response was interrupted.`;
  } finally {
    setBusy(false);
  }
});

async function refresh() {
  clearPreview();
  setBusy(true);
  try {
    await loadDesk();
    message.textContent = "";
  } catch (error) {
    message.textContent = error.message;
  } finally {
    setBusy(false);
  }
}

document.querySelector("#preview-button").addEventListener("click", async () => {
  if (!form.reportValidity()) return;
  const body = JSON.stringify(ticket());
  clearPreview();
  setBusy(true);
  document.querySelector("#preview-status").textContent = "Checking current balances…";
  try {
    const result = await request("/rfqs/preview", {
      method: "POST", headers: { "Content-Type": "application/json" }, body,
    });
    document.querySelector("#preview-status").textContent = result.eligible ? "Eligible at preview time" : "Not eligible";
    document.querySelector("#preview-detail").textContent = "Credit, inventory, and RFQ history are unchanged by this preview.";
    const labels = {
      INSUFFICIENT_NOTIONAL: "Insufficient bond notional.",
      INSUFFICIENT_CREDIT: "Insufficient counterparty credit.",
    };
    document.querySelector("#preview-reasons").replaceChildren(...result.reasons.map((reason) => {
      const item = document.createElement("li");
      item.textContent = labels[reason] || reason;
      return item;
    }));
    document.querySelector("#remaining-credit").textContent = currency.format(result.remainingCredit);
    document.querySelector("#remaining-notional").textContent = currency.format(result.remainingNotional);
    document.querySelector("#preview-balances").hidden = false;
  } catch (error) {
    document.querySelector("#preview-status").textContent = "Preview unavailable";
    document.querySelector("#preview-detail").textContent = error.message;
  } finally {
    setBusy(false);
  }
});

refreshButton.addEventListener("click", refresh);
refresh();
