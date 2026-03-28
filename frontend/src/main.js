import "./style.css";

const app = document.querySelector("#app");

app.innerHTML = `
  <main class="card">
    <h1>Hello from Node + Vite</h1>
    <p class="hint">Edit <code>src/main.js</code> and save to hot-reload.</p>
    <button type="button" id="counter" aria-live="polite">Clicks: 0</button>
  </main>
`;

let count = 0;
const btn = document.querySelector("#counter");
btn.addEventListener("click", () => {
  count += 1;
  btn.textContent = `Clicks: ${count}`;
});
