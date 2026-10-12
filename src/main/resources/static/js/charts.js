// Hover or focus a mark (a donut arc, a bar) to read its name, value and detail in a small tooltip.
// Every chart card has its own .tooltip, and every readable mark carries data-name and data-amount, and optionally
// data-share (shown after the amount) and data-extra (an extra line underneath).
// The values are also in each chart's table, so nothing depends on this script.
document.querySelectorAll('.chart-card').forEach(function (card) {
    var tip = card.querySelector('.tooltip');
    if (!tip) return;

    function show(mark, x, y) {
        tip.replaceChildren();
        var value = document.createElement('strong');
        value.textContent = mark.dataset.share ? mark.dataset.amount + ' · ' + mark.dataset.share : mark.dataset.amount;
        var name = document.createElement('span');
        name.textContent = mark.dataset.name;
        tip.append(value, name);
        if (mark.dataset.extra) {
            var extra = document.createElement('span');
            extra.className = 'extra';
            extra.textContent = mark.dataset.extra;
            tip.append(extra);
        }
        tip.hidden = false;
        var box = card.getBoundingClientRect();
        var left = Math.min(Math.max(x - box.left, 0), box.width - tip.offsetWidth);
        tip.style.left = left + 'px';
        tip.style.top = (y - box.top - tip.offsetHeight - 12) + 'px';
    }

    card.querySelectorAll('[data-name]').forEach(function (mark) {
        mark.addEventListener('pointermove', function (e) { show(mark, e.clientX, e.clientY); });
        mark.addEventListener('focus', function () {
            var r = mark.getBoundingClientRect();
            show(mark, r.left + r.width / 2, r.top);
        });
        mark.addEventListener('pointerleave', function () { tip.hidden = true; });
        mark.addEventListener('blur', function () { tip.hidden = true; });
    });
});
