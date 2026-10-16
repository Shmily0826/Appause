/* Appause landing v3 · page script (hero demo with one-time autoplay + mobile download bar)
   Default ("live"): a working copy of the pause screen the visitor can tap through.
   "?demo=frames": real emulator screenshots, switched with two tabs.
   Without JS the stage simply shows the real "counting" screenshot. */
(function () {
  var stage = document.querySelector('[data-demo]');
  if (!stage) { return; }

  var variant = new URLSearchParams(location.search).get('demo') === 'frames' ? 'frames' : 'live';
  var phone = stage.querySelector('[data-phone]');
  var steps = [].slice.call(stage.querySelectorAll('[data-step]'));
  var live = stage.querySelector('[data-live]');
  var replay = stage.querySelector('[data-replay]');
  var say = function (key) { live.textContent = stage.getAttribute('data-say-' + key) || ''; };

  var setStep = function (n, waiting) {
    steps.forEach(function (li) {
      var on = Number(li.getAttribute('data-step')) === n;
      li.classList.toggle('is-now', on);
      li.classList.toggle('is-wait', on && !!waiting);
      if (on) { li.setAttribute('aria-current', 'step'); } else { li.removeAttribute('aria-current'); }
    });
  };

  document.documentElement.setAttribute('data-demo-variant', variant);

  if (variant === 'frames') {
    var frames = { counting: phone.querySelector('[data-frame="counting"]'), done: phone.querySelector('[data-frame="done"]') };
    var tabs = stage.querySelector('[data-tabs]');
    tabs.hidden = false;
    var show = function (name) {
      frames.counting.hidden = name !== 'counting';
      frames.done.hidden = name !== 'done';
      [].forEach.call(tabs.querySelectorAll('button'), function (b) {
        b.setAttribute('aria-pressed', b.getAttribute('data-show') === name ? 'true' : 'false');
      });
      setStep(name === 'counting' ? 2 : 3, name === 'counting');
    };
    tabs.addEventListener('click', function (e) {
      var b = e.target.closest('button[data-show]');
      if (b) { show(b.getAttribute('data-show')); }
    });
    show('counting');
    return;
  }

  /* ---------- live variant ---------- */
  var tpl = stage.querySelector('[data-live-tpl]');
  phone.innerHTML = '';
  phone.appendChild(tpl.content.cloneNode(true));

  var home = phone.querySelector('.home');
  var pause = phone.querySelector('.pause');
  var opened = phone.querySelector('.opened');
  var target = phone.querySelector('.home-app.is-target');
  var ring = phone.querySelector('.ring');
  var arc = phone.querySelector('.ring-arc');
  var num = phone.querySelector('.ring-num');
  var cont = phone.querySelector('.continue');
  var cancel = phone.querySelector('.cancel');
  var chips = [].slice.call(phone.querySelectorAll('.chip'));
  var title = phone.querySelector('.pause-name');

  var seconds = Number(stage.getAttribute('data-seconds')) || 5;
  var circumference = 2 * Math.PI * 67;
  arc.style.strokeDasharray = circumference;

  // Same blend as CountdownRing.kt: lerp(primary #2962FF, tertiary #FF8F00, progress).
  var from = [0x29, 0x62, 0xff];
  var to = [0xff, 0x8f, 0x00];
  var mix = function (p) {
    return 'rgb(' + from.map(function (c, i) { return Math.round(c + (to[i] - c) * p); }).join(',') + ')';
  };

  var raf = 0;
  var startedAt = 0;
  var auto = false;      // true while the one-time autoplay is running
  var autoTimer = 0;
  var hint = phone.querySelector('.home-hint');
  var hintAgain = hint.getAttribute('data-hint-again') || hint.textContent;
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Any real interaction ends the autoplay; the visitor's action wins.
  var stopAuto = function () {
    if (!auto) { return; }
    auto = false;
    clearTimeout(autoTimer);
  };

  var drawRing = function (p) {
    arc.style.strokeDashoffset = circumference * (1 - p);
    arc.style.stroke = mix(p);
  };

  var tick = function (now) {
    var p = Math.max(0, Math.min(1, (now - startedAt) / (seconds * 1000)));
    drawRing(p);
    num.textContent = String(Math.max(1, Math.ceil(seconds * (1 - p))));
    if (p < 1) { raf = requestAnimationFrame(tick); return; }
    ring.classList.add('is-done');
    arc.style.stroke = 'rgb(255,143,0)';
    cont.disabled = false;
    setStep(3);
    if (auto) {
      // The one-time autoplay ends by going back to the home screen,
      // where the bubble now invites the visitor to try it themselves.
      autoTimer = setTimeout(function () { auto = false; goHome(false); hint.textContent = hintAgain; }, 1600);
      return;
    }
    say('done');
  };

  var reset = function () {
    cancelAnimationFrame(raf);
    ring.classList.remove('is-done');
    drawRing(0);
    num.textContent = String(seconds);
    cont.disabled = true;
    chips.forEach(function (c) { c.setAttribute('aria-pressed', 'false'); });
  };

  var goHome = function (announce) {
    reset();
    pause.classList.remove('is-up');
    opened.hidden = true;
    home.hidden = false;
    // Let the slide-down finish before hiding the pause layer from assistive tech.
    setTimeout(function () { if (!pause.classList.contains('is-up')) { pause.hidden = true; } }, 340);
    replay.hidden = true;
    setStep(1);
    if (announce) { say('home'); target.focus(); }
  };

  var openPause = function () {
    reset();
    pause.hidden = false;
    // Force a layout so the slide-up transition runs from the hidden position.
    void pause.offsetWidth;
    pause.classList.add('is-up');
    setStep(2, true);
    // Autoplay must not move focus or talk over the page; only a real tap does.
    if (!auto) { say('pause'); title.focus(); }
    startedAt = performance.now();
    raf = requestAnimationFrame(tick);
  };

  // Reduced motion: no autoplay. Show the finished pause screen as a still frame instead.
  var showDoneFrame = function () {
    reset();
    pause.hidden = false;
    pause.classList.add('is-up');
    drawRing(1);
    ring.classList.add('is-done');
    arc.style.stroke = 'rgb(255,143,0)';
    cont.disabled = false;
    setStep(3);
  };

  target.addEventListener('click', function () { stopAuto(); openPause(); });
  cancel.addEventListener('click', function () { stopAuto(); goHome(true); hint.textContent = hintAgain; });
  cont.addEventListener('click', function () {
    stopAuto();
    pause.classList.remove('is-up');
    pause.hidden = true;
    home.hidden = true;
    opened.hidden = false;
    replay.hidden = false;
    say('open');
    replay.focus();
  });
  chips.forEach(function (c) {
    c.addEventListener('click', function () {
      stopAuto();
      chips.forEach(function (o) { o.setAttribute('aria-pressed', o === c ? 'true' : 'false'); });
    });
  });
  replay.addEventListener('click', function () { goHome(false); hint.textContent = hintAgain; target.focus(); });

  goHome(false);

  if (reduce) {
    showDoneFrame();
  } else if ('IntersectionObserver' in window) {
    // Play the pause once, the first time half the stage is on screen. Never loop.
    var io = new IntersectionObserver(function (entries) {
      if (!entries[0].isIntersecting) { return; }
      io.disconnect();
      auto = true;
      openPause();
    }, { threshold: 0.5 });
    io.observe(stage);
  }
})();

/* Mobile download bar: shows once the hero has scrolled away,
   and steps aside while the download section itself is on screen. */
(function () {
  var bar = document.querySelector('[data-bar]');
  var hero = document.querySelector('.hero');
  var dl = document.getElementById('download');
  if (!bar || !hero || !dl || !('IntersectionObserver' in window)) { return; }
  var heroOut = false;
  var dlIn = false;
  var apply = function () { bar.classList.toggle('show', heroOut && !dlIn); };
  new IntersectionObserver(function (e) { heroOut = !e[0].isIntersecting; apply(); }).observe(hero);
  new IntersectionObserver(function (e) { dlIn = e[0].isIntersecting; apply(); }).observe(dl);
})();
