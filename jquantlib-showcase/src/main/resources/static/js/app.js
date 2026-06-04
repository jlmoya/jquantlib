// Shared client helpers for the JQuantLib Showcase.
// All demos call JSON endpoints and render results + Chart.js charts.

const _charts = {};

async function qFetch(url) {
    const r = await fetch(url);
    if (!r.ok) {
        let msg = r.status + ' ' + r.statusText;
        try {
            const e = await r.json();
            msg = e.message || e.error || msg;
        } catch (_) { /* non-JSON error body */ }
        throw new Error(msg);
    }
    return r.json();
}

function fmt(x, d = 4) {
    if (x === null || x === undefined || Number.isNaN(x)) return '—';
    return Number(x).toLocaleString(undefined, {minimumFractionDigits: d, maximumFractionDigits: d});
}

function setBusy(btn, busy) {
    if (!btn) return;
    if (!btn.dataset.label) btn.dataset.label = btn.textContent;
    btn.disabled = busy;
    btn.textContent = busy ? 'Computing…' : btn.dataset.label;
}

function showError(elId, msg) {
    const el = document.getElementById(elId);
    if (!el) return;
    // Build with textContent (not innerHTML) so a server-provided message can
    // never inject markup.
    el.replaceChildren();
    const div = document.createElement('div');
    div.className = 'alert alert-danger mb-0';
    div.textContent = '⚠ ' + msg;
    el.appendChild(div);
}

// Set plain text safely (used for summaries and any echoed input).
function setText(elId, text) {
    const el = document.getElementById(elId);
    if (el) el.textContent = text == null ? '' : text;
}

function formParams(form) {
    const p = new URLSearchParams();
    for (const [k, v] of new FormData(form).entries()) p.append(k, v);
    return p.toString();
}

// Create or replace a Chart.js chart on a canvas id.
function drawChart(canvasId, config) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    if (_charts[canvasId]) _charts[canvasId].destroy();
    config.options = Object.assign({
        responsive: true,
        maintainAspectRatio: false,
        interaction: {mode: 'index', intersect: false},
        plugins: {legend: {position: 'top'}}
    }, config.options || {});
    _charts[canvasId] = new Chart(ctx, config);
}

function lineSet(label, data, color, opts = {}) {
    return Object.assign({
        label: label,
        data: data,
        borderColor: color,
        backgroundColor: color + '22',
        borderWidth: 2,
        pointRadius: 0,
        tension: 0.12
    }, opts);
}

// A small palette used across the demos.
const COLORS = {
    blue: '#0d6efd', green: '#198754', red: '#dc3545', amber: '#fd7e14',
    purple: '#6f42c1', teal: '#20c997', gray: '#6c757d', ink: '#0b132b'
};

function metric(label, value, unit = '') {
    return '<div class="metric">'
        + '<div class="label">' + label + '</div>'
        + '<div class="value">' + value + (unit ? '<span class="fs-6 text-muted"> ' + unit + '</span>' : '') + '</div>'
        + '</div>';
}
