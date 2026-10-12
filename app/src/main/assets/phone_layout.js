(function () {
  if (window.__whatswebInstalled) {
    if (window.whatswebSetPhone) window.whatswebSetPhone(!!window.__whatswebPhone);
    if (window.whatswebSetQuiet) window.whatswebSetQuiet(!!window.__whatswebQuiet);
    if (window.whatswebSetHideAi) window.whatswebSetHideAi(!!window.__whatswebHideAi);
    return true;
  }
  window.__whatswebInstalled = true;

  function postHost(payload) {
    var host = window.WhatswebHost;
    if (!host || !host.postMessage) return;
    host.postMessage(JSON.stringify(payload));
  }

  // Moving WhatsApp's own buttons out of their React tree makes a later update throw,
  // and WhatsApp replaces the page with "Unexpected error occurred".
  if (!window.__whatswebDomGuard) {
    window.__whatswebDomGuard = true;
    var removeChild = Node.prototype.removeChild;
    Node.prototype.removeChild = function (child) {
      if (child && child.parentNode !== this) return child;
      return removeChild.call(this, child);
    };
    var insertBefore = Node.prototype.insertBefore;
    Node.prototype.insertBefore = function (node, before) {
      if (before && before.parentNode !== this) return node;
      return insertBefore.call(this, node, before);
    };
  }

  var style = document.createElement("style");
  style.id = "whatsweb-layout";
  style.textContent = [
    "html[data-whatsweb='phone'], html[data-whatsweb='phone'] body {",
    "  overflow-x: hidden !important;",
    "  min-width: 0 !important;",
    "  width: 100% !important;",
    "}",
    "html[data-whatsweb='phone'] [data-testid='chatlist-header'][data-whatsweb-nav] {",
    "  position: absolute !important;",
    "  width: 0 !important;",
    "  height: 0 !important;",
    "  min-width: 0 !important;",
    "  max-width: 0 !important;",
    "  flex: 0 0 0px !important;",
    "  overflow: hidden !important;",
    "  visibility: hidden !important;",
    "  pointer-events: none !important;",
    "  padding: 0 !important;",
    "}",
    "html[data-whatsweb='phone'] [data-whatsweb-navtip] {",
    "  display: none !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='list'] [data-whatsweb-role='list'] {",
    "  flex: 1 1 auto !important;",
    "  width: auto !important;",
    "  max-width: none !important;",
    "  min-width: 0 !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='list']:not([data-opening]) [data-whatsweb-chatcol] {",
    "  display: none !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='list'] [data-testid='drawer-left'] {",
    "  position: absolute !important;",
    "  left: 0 !important;",
    "  top: 0 !important;",
    "  width: 100% !important;",
    "  height: 100% !important;",
    "  max-width: none !important;",
    "  margin: 0 !important;",
    "  transform: none !important;",
    "  flex: 1 1 auto !important;",
    "}",
    "html[data-whatsweb='phone'] [data-testid='drawer-middle'],",
    "html[data-whatsweb='phone'][data-pane='chat'] [data-testid='drawer-left'] {",
    "  display: none !important;",
    "}",
    "html[data-whatsweb='phone'][data-opening] [data-whatsweb-role='chat'],",
    "html[data-whatsweb='phone'][data-opening] [data-whatsweb-chatcol] {",
    "  display: block !important;",
    "  position: absolute !important;",
    "  left: 0 !important;",
    "  top: 0 !important;",
    "  width: 100% !important;",
    "  height: 100% !important;",
    "  visibility: hidden !important;",
    "  pointer-events: none !important;",
    "}",
    "html[data-whatsweb='phone'] [data-whatsweb-pad] {",
    "  box-sizing: border-box !important;",
    "  padding-bottom: 80px !important;",
    "}",
    "#whatsweb-tabbar {",
    "  position: fixed;",
    "  left: 0;",
    "  right: 0;",
    "  bottom: 0;",
    "  z-index: 1000;",
    "  height: 72px;",
    "  display: flex;",
    "  align-items: stretch;",
    "  justify-content: space-around;",
    "  box-shadow: 0 -1px 0 rgba(0, 0, 0, 0.12);",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot] {",
    "  position: relative;",
    "  width: auto;",
    "  height: auto;",
    "  flex: 1 1 0;",
    "  display: flex;",
    "  flex-direction: column;",
    "  align-items: center;",
    "  justify-content: flex-start;",
    "  padding-top: 8px;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot]:not([data-whatsweb-slot='fabs'])::before {",
    "  content: '';",
    "  position: absolute;",
    "  top: 6px;",
    "  left: 50%;",
    "  width: 64px;",
    "  height: 32px;",
    "  transform: translateX(-50%);",
    "  border-radius: 16px;",
    "  background: transparent;",
    "  z-index: 0;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot][data-whatsweb-selected='1']:not([data-whatsweb-slot='fabs'])::before {",
    "  background: #d8fdd2;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-tab-label] {",
    "  position: absolute;",
    "  left: 0;",
    "  right: 0;",
    "  bottom: 8px;",
    "  z-index: 2;",
    "  margin: 0;",
    "  font-size: 12px;",
    "  line-height: 14px;",
    "  font-weight: 500;",
    "  text-align: center;",
    "  color: rgba(0, 0, 0, 0.6);",
    "  pointer-events: none;",
    "}",
    "@media (prefers-color-scheme: dark) {",
    "  #whatsweb-tabbar [data-whatsweb-slot][data-whatsweb-selected='1']:not([data-whatsweb-slot='fabs'])::before {",
    "    background: #0a3329;",
    "  }",
    "  #whatsweb-tabbar [data-whatsweb-tab-label] {",
    "    color: rgba(255, 255, 255, 0.6);",
    "  }",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot='fabs'] {",
    "  position: absolute;",
    "  left: 83.333%;",
    "  bottom: calc(100% + 16px);",
    "  width: 40px;",
    "  height: auto;",
    "  flex: none;",
    "  padding-top: 0;",
    "  transform: translateX(-50%);",
    "  display: flex;",
    "  flex-direction: column;",
    "  align-items: center;",
    "  gap: 16px;",
    "  z-index: 2;",
    "  pointer-events: none;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot='fabs']::before {",
    "  content: none;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot='fabs'] button:disabled,",
    "#whatsweb-tabbar [data-whatsweb-slot='fabs'] [aria-disabled='true'] {",
    "  display: none !important;",
    "}",
    "#whatsweb-tabbar button {",
    "  position: relative !important;",
    "  left: auto !important;",
    "  top: auto !important;",
    "  right: auto !important;",
    "  bottom: auto !important;",
    "  margin: 0 !important;",
    "  transform: none !important;",
    "  z-index: 1;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot]:not([data-whatsweb-slot='fabs']) button {",
    "  position: absolute !important;",
    "  left: 0 !important;",
    "  top: 0 !important;",
    "  right: 0 !important;",
    "  bottom: 0 !important;",
    "  width: auto !important;",
    "  height: auto !important;",
    "  display: flex !important;",
    "  flex-direction: column !important;",
    "  align-items: center !important;",
    "  justify-content: flex-start !important;",
    "  padding: 10px 0 0 !important;",
    "  background: transparent !important;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot]:not([data-whatsweb-slot='fabs']) button :not(svg):not(path):not(title):not(:has(svg)) {",
    "  display: none !important;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot='fabs'] button {",
    "  pointer-events: auto;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-slot]:not([data-whatsweb-slot='fabs']) button::after {",
    "  content: none !important;",
    "  display: none !important;",
    "}",
    "[data-whatsweb-quiet-room] {",
    "  box-sizing: border-box !important;",
    "  padding-right: 12px !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='chat'] [data-whatsweb-back-room] {",
    "  margin-left: 32px !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='chat'] [data-whatsweb-chat-menu] {",
    "  transform: translateX(-28px) !important;",
    "}",
    "html[data-whatsweb='phone'][data-pane='chat'] [data-whatsweb-chat-search] {",
    "  display: none !important;",
    "}",
    "html[data-whatsweb='phone'] [data-testid='popover']:has([data-testid='dropdown']) {",
    "  left: auto !important;",
    "  right: 8px !important;",
    "  max-width: calc(100% - 16px) !important;",
    "}",
    "#whatsweb-tabbar [data-whatsweb-badge] {",
    "  position: absolute !important;",
    "  top: 4px !important;",
    "  left: calc(50% + 8px) !important;",
    "  right: auto !important;",
    "  bottom: auto !important;",
    "  z-index: 2 !important;",
    "  margin: 0 !important;",
    "}",
    "html[data-whatsweb='phone'] [data-whatsweb-drawer] {",
    "  position: fixed !important;",
    "  left: 0 !important;",
    "  top: 0 !important;",
    "  right: 0 !important;",
    "  width: 100% !important;",
    "  height: 100% !important;",
    "  max-width: none !important;",
    "  transform: none !important;",
    "  margin: 0 !important;",
    "  z-index: 1100 !important;",
    "}",
    "html[data-whatsweb='phone'][data-whatsweb-drawer='1'] #whatsweb-tabbar {",
    "  display: none !important;",
    "}",
    "html[data-whatsweb='phone'] [role='dialog'] {",
    "  box-sizing: border-box !important;",
    "  min-width: 0 !important;",
    "  width: 100% !important;",
    "  max-width: 100% !important;",
    "}",
    "html[data-whatsweb='phone'] [data-testid='confirm-popup'] {",
    "  box-sizing: border-box !important;",
    "  width: auto !important;",
    "  max-width: calc(100% - 32px) !important;",
    "}"
  ].join("\n");
  document.documentElement.appendChild(style);

  function clearInline(el) {
    if (!el) return;
    el.style.display = "";
    el.style.width = "";
    el.style.maxWidth = "";
    el.style.minWidth = "";
    el.style.flex = "";
  }

  function clearMarks() {
    var nodes = document.querySelectorAll("[data-whatsweb-role], [data-whatsweb-fill]");
    for (var i = 0; i < nodes.length; i++) {
      delete nodes[i].dataset.whatswebRole;
      delete nodes[i].dataset.whatswebFill;
      clearInline(nodes[i]);
    }
  }

  function rowChild(start) {
    if (!start) return null;
    var column = start;
    while (column.parentElement && window.getComputedStyle(column.parentElement).flexDirection !== "row") {
      column = column.parentElement;
    }
    return column.parentElement ? column : null;
  }

  function introColumn() {
    return rowChild(document.querySelector("[data-testid='intro-panel']"));
  }

  function navRail() {
    var rails = document.querySelectorAll("[data-testid='chatlist-header']");
    for (var i = 0; i < rails.length; i++) {
      if (rails[i].querySelector("[data-testid='navbar-primary-section']")) return rails[i];
    }
    return rails.length ? rails[0] : null;
  }

  function shellColumns(rail) {
    var shell = rail && rail.parentElement;
    if (!shell || !document.getElementById("pane-side")) return null;
    var list = null;
    var chat = null;
    var kids = shell.children;
    for (var i = 0; i < kids.length; i++) {
      var el = kids[i];
      if (el === rail) continue;
      var style = window.getComputedStyle(el);
      if (style.position === "absolute" || style.position === "fixed") continue;
      if (el.querySelector("#pane-side, #side")) list = el;
      else if (!chat && el.getBoundingClientRect().width > 20) chat = el;
    }
    if (!list) return null;
    return {
      list: list,
      chat: chat || list,
      shell: shell,
      rail: rail,
      fill: [document.getElementById("side"), document.getElementById("pane-side")]
    };
  }

  function companionPanes() {
    var fromRail = shellColumns(navRail());
    if (fromRail) return fromRail;
    var listCol = rowChild(document.getElementById("pane-side") || document.getElementById("side"));
    var introCol = introColumn();
    if (listCol && introCol && listCol === introCol) introCol = null;
    var rail = navRail();
    var fill = [document.getElementById("side"), document.getElementById("pane-side")];
    if (listCol && introCol) {
      return { list: listCol, chat: introCol, shell: listCol.parentElement, rail: rail, fill: fill };
    }
    var openChat = document.getElementById("main");
    if (listCol && openChat) {
      return { list: listCol, chat: openChat, shell: listCol.parentElement, rail: rail, fill: fill };
    }
    var legacyList = document.getElementById("pane-side");
    var legacyChat = document.getElementById("main");
    if (legacyList && legacyChat) {
      return { list: legacyList, chat: legacyChat, shell: legacyList.parentElement, rail: null, fill: [] };
    }
    return null;
  }

  function loginWrap() {
    var wrap = document.querySelector("#app .app-wrapper-web");
    if (wrap) return wrap;
    var app = document.getElementById("app");
    if (!app) return null;
    var nodes = app.querySelectorAll("div");
    var limit = Math.min(nodes.length, 40);
    var available = window.innerWidth;
    for (var i = 0; i < limit; i++) {
      var min = parseFloat(window.getComputedStyle(nodes[i]).minWidth) || 0;
      if (min > available + 40 && nodes[i].parentElement) return nodes[i].parentElement;
    }
    return null;
  }

  function releaseLoginEl(el) {
    if (!el) return;
    el.style.zoom = "";
    el.style.transform = "";
    clearInline(el);
    el.style.maxWidth = "";
  }

  function fitLogin() {
    var wrap = loginWrap();
    if (!wrap) return;
    wrap.style.zoom = "";
    var current = window.__whatswebLoginEl;
    if (!current || !document.body.contains(current)) {
      var target = null;
      var needed = 0;
      var kids = wrap.querySelectorAll("div");
      var limit = Math.min(kids.length, 40);
      var available = window.innerWidth;
      for (var i = 0; i < limit; i++) {
        var el = kids[i];
        var rect = el.getBoundingClientRect();
        if (rect.width < 80 || rect.height < 80) continue;
        var min = parseFloat(window.getComputedStyle(el).minWidth) || 0;
        if (min > available + 40 && min > needed) {
          needed = min;
          target = el;
        }
      }
      if (!target) return;
      if (current && current !== target) releaseLoginEl(current);
      current = target;
      window.__whatswebLoginEl = current;
    }
    current.style.zoom = "";
    current.style.transform = "";
    current.style.minWidth = "0px";
    current.style.width = "100%";
    current.style.maxWidth = "100%";
  }

  function releaseWidthFit(el) {
    if (!el) return;
    el.style.minWidth = "";
    el.style.maxWidth = "";
    el.style.flexShrink = "";
    el.style.boxSizing = "";
    delete el.dataset.whatswebFit;
  }

  function clearLoginWidth() {
    var fitted = document.querySelectorAll("[data-whatsweb-fit]");
    for (var i = 0; i < fitted.length; i++) releaseWidthFit(fitted[i]);
    var scrollers = document.querySelectorAll("[data-whatsweb-fit-scroll]");
    for (var j = 0; j < scrollers.length; j++) {
      scrollers[j].style.overflowX = "";
      delete scrollers[j].dataset.whatswebFitScroll;
    }
  }

  function fitLoginWidth() {
    var app = document.getElementById("app");
    if (!app) return;
    var nodes = app.querySelectorAll("div");
    var scroller = null;
    for (var i = 0; i < nodes.length; i++) {
      var style = window.getComputedStyle(nodes[i]);
      var scrolls = style.overflowX === "auto" || style.overflowX === "scroll" || style.overflowY === "auto" || style.overflowY === "scroll";
      if (scrolls && nodes[i].scrollWidth > nodes[i].clientWidth + 2 && nodes[i].clientWidth > 40) {
        scroller = nodes[i];
        break;
      }
    }
    if (!scroller) return;
    var box = scroller.getBoundingClientRect();
    var limit = scroller.clientWidth;
    var kids = scroller.querySelectorAll("div");
    for (var k = 0; k < kids.length; k++) {
      var el = kids[k];
      var rect = el.getBoundingClientRect();
      if (rect.width <= limit + 1) continue;
      if (rect.right < box.left + 8 || rect.left > box.right - 8) continue;
      el.dataset.whatswebFit = "1";
      el.style.minWidth = "0px";
      el.style.maxWidth = "100%";
      el.style.flexShrink = "1";
      el.style.boxSizing = "border-box";
    }
    if (scroller.scrollWidth > scroller.clientWidth + 1) {
      scroller.dataset.whatswebFitScroll = "1";
      scroller.style.overflowX = "hidden";
    }
  }

  function alignLoginTop() {
    var canvas = document.querySelector("#app canvas");
    if (!canvas) return;
    var card = canvas;
    while (card && card !== document.body) {
      var style = window.getComputedStyle(card);
      var tall = card.getBoundingClientRect().height > 400;
      if (style.display === "flex" && style.flexDirection === "column" && style.justifyContent === "center" && tall) {
        card.dataset.whatswebAlign = "1";
        card.style.justifyContent = "flex-start";
        card.style.flexGrow = "0";
        card.style.height = "auto";
        return;
      }
      card = card.parentElement;
    }
  }

  function clearLoginAlign() {
    var nodes = document.querySelectorAll("[data-whatsweb-align]");
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].style.justifyContent = "";
      nodes[i].style.flexGrow = "";
      nodes[i].style.height = "";
      delete nodes[i].dataset.whatswebAlign;
    }
  }

  function clearLoginFit() {
    var wrap = document.querySelector("#app .app-wrapper-web");
    if (wrap) wrap.style.zoom = "";
    releaseLoginEl(window.__whatswebLoginEl);
    window.__whatswebLoginEl = null;
    clearLoginWidth();
    clearLoginAlign();
  }

  var NAV_KIND = {
    "chats": "chats",
    "chat": "chats",
    "chat-filled": "chats",
    "wds-ic-chat-filled": "chats",
    "wds-ic-chat": "chats",
    "wa-chat": "chats",
    "status": "status",
    "status-outline": "status",
    "status-refreshed": "status",
    "wds-ic-status": "status",
    "updates": "status",
    "calls": "calls",
    "calls-outline": "calls",
    "phone": "calls",
    "call": "calls",
    "ic-call": "calls",
    "new chat": "plus",
    "new-chat": "plus",
    "new-chat-outline": "plus",
    "wds-ic-new-chat-filled": "plus",
    "chat-plus": "plus",
    "plus": "plus",
    "meta ai": "meta",
    "meta-ai": "meta",
    "ai": "meta",
    "wds-ic-ai": "meta",
    "channels": "optional",
    "channel": "optional",
    "channels-outline": "optional",
    "wds-ic-channels": "optional",
    "communities": "optional",
    "community": "optional",
    "community-outline": "optional",
    "communities-outline": "optional",
    "wds-ic-communities": "optional",
    "newsletter": "optional",
    "newsletter-outline": "optional",
    "media": "optional",
    "you": "optional",
    "settings": "optional",
    "settings-outline": "optional",
    "settings-refreshed": "optional",
    "menu": "optional",
    "more": "optional",
    "ic-more-vert": "optional",
    "ic-filter": "optional",
    "ic-search": "search",
    "search": "search",
    "search-refreshed": "search",
    "back": "back",
    "arrow-back": "back",
    "ic-arrow-back": "back",
    "back-refreshed": "back",
    "go back": "back"
  };

  function controlTokens(button) {
    var tokens = [];
    function add(value) {
      var token = String(value || "").replace(/\s+/g, " ").trim().toLowerCase();
      if (token && tokens.indexOf(token) < 0) tokens.push(token);
    }
    if (!button) return tokens;
    add(button.getAttribute("aria-label"));
    var icons = button.querySelectorAll("[data-icon]");
    for (var i = 0; i < icons.length; i++) add(icons[i].getAttribute("data-icon"));
    var titles = button.querySelectorAll("svg title");
    for (var t = 0; t < titles.length; t++) add(titles[t].textContent);
    add(button.getAttribute("data-testid"));
    return tokens;
  }

  function controlKind(button) {
    var tokens = controlTokens(button);
    for (var i = 0; i < tokens.length; i++) {
      if (NAV_KIND[tokens[i]]) return NAV_KIND[tokens[i]];
    }
    return "";
  }

  function findKind(kind) {
    var buttons = document.querySelectorAll("button, [role='button']");
    for (var i = 0; i < buttons.length; i++) {
      if (controlKind(buttons[i]) === kind) return buttons[i];
    }
    return null;
  }

  function trackMoved(el) {
    if (!el) return;
    var list = window.__whatswebMoved || (window.__whatswebMoved = []);
    if (list.indexOf(el) === -1) list.push(el);
  }

  function rememberHome(el) {
    if (!el || el.__whatswebParent) return;
    el.__whatswebParent = el.parentNode;
    el.__whatswebNext = el.nextSibling;
  }

  function restoreHome(el) {
    if (!el) return;
    var parent = el.__whatswebParent;
    if (parent && parent.isConnected) {
      var next = el.__whatswebNext;
      if (next && next.parentNode !== parent) next = null;
      parent.insertBefore(el, next);
    } else if (el.parentNode && (el.parentNode.id === "whatsweb-tabbar" || el.parentNode.dataset.whatswebSlot)) {
      var header = document.querySelector("[data-testid='chatlist-header']");
      if (header) header.appendChild(el);
    }
    el.__whatswebParent = null;
    el.__whatswebNext = null;
  }

  function clearMovedStyle(el) {
    if (!el) return;
    el.style.position = "";
    el.style.left = "";
    el.style.right = "";
    el.style.top = "";
    el.style.bottom = "";
    el.style.zIndex = "";
    el.style.margin = "";
    el.style.display = "";
    el.style.visibility = "";
    el.style.transform = "";
  }

  function barHost() {
    return document.getElementById("app") || document.body;
  }

  function ensureBar() {
    var bar = document.getElementById("whatsweb-tabbar");
    if (!bar) {
      bar = document.createElement("div");
      bar.id = "whatsweb-tabbar";
      bar.setAttribute("role", "navigation");
      ["chats", "status", "calls", "fabs"].forEach(function (name) {
        var slot = document.createElement("div");
        slot.dataset.whatswebSlot = name;
        bar.appendChild(slot);
      });
    }
    var host = barHost();
    if (bar.parentNode !== host) host.appendChild(bar);
    return bar;
  }

  function park(el, slot) {
    if (!el || !slot) return;
    var kind = controlKind(el);
    var kids = slot.querySelectorAll("button, [role='button']");
    for (var i = 0; i < kids.length; i++) {
      if (kids[i] !== el && kind && kind !== "optional" && controlKind(kids[i]) === kind) kids[i].remove();
    }
    rememberHome(el);
    trackMoved(el);
    clearMovedStyle(el);
    if (el.parentNode !== slot) slot.appendChild(el);
  }

  function chatsBadge(button) {
    var marked = document.querySelector("[data-whatsweb-badge]");
    if (marked) return marked;
    var sibling = button && button.nextElementSibling;
    if (sibling && sibling.querySelector && sibling.querySelector("[role='status']")) return sibling;
    var parent = button && button.__whatswebParent;
    if (!parent) return null;
    var kids = parent.children;
    for (var i = 0; i < kids.length; i++) {
      if (kids[i].querySelector && kids[i].querySelector("[role='status']")) return kids[i];
    }
    return null;
  }

  function clearBottomNav() {
    var moved = window.__whatswebMoved || [];
    var bar = document.getElementById("whatsweb-tabbar");
    if (!window.__whatswebNavActive && !bar && moved.length === 0) return;
    for (var i = 0; i < moved.length; i++) {
      clearMovedStyle(moved[i]);
      if (moved[i].dataset) delete moved[i].dataset.whatswebBadge;
      restoreHome(moved[i]);
    }
    window.__whatswebMoved = [];
    window.__whatswebNavActive = false;
    var badges = document.querySelectorAll("[data-whatsweb-badge]");
    for (var i = 0; i < badges.length; i++) {
      clearMovedStyle(badges[i]);
      delete badges[i].dataset.whatswebBadge;
      restoreHome(badges[i]);
    }
    var bar = document.getElementById("whatsweb-tabbar");
    if (bar) bar.remove();
    var header = navRail();
    if (header) delete header.dataset.whatswebNav;
    var hidden = document.querySelectorAll("[data-whatsweb-navhide]");
    for (var h = 0; h < hidden.length; h++) {
      hidden[h].style.display = "";
      delete hidden[h].dataset.whatswebNavhide;
    }
    var cols = document.querySelectorAll("[data-whatsweb-chatcol]");
    for (var c = 0; c < cols.length; c++) {
      cols[c].style.display = "";
      delete cols[c].dataset.whatswebChatcol;
    }
    var pads = document.querySelectorAll("[data-whatsweb-pad]");
    for (var p = 0; p < pads.length; p++) {
      pads[p].style.paddingBottom = "";
      pads[p].style.boxSizing = "";
      delete pads[p].dataset.whatswebPad;
    }
    clearOverlay();
  }

  function collapseChatColumn() {
    var shell = document.querySelector("[data-whatsweb-role='shell']");
    var chat = document.querySelector("[data-whatsweb-role='chat']");
    if (!shell || !chat) return;
    var child = chat;
    while (child.parentElement && child.parentElement !== shell) child = child.parentElement;
    if (!child || child === shell || child.dataset.whatswebRole === "list") return;
    child.dataset.whatswebChatcol = "1";
  }

  function padList() {
    var list = document.querySelector("[data-whatsweb-role='list']");
    if (!list) return;
    var scroller = null;
    var nodes = list.querySelectorAll("div");
    for (var i = 0; i < nodes.length; i++) {
      var style = window.getComputedStyle(nodes[i]);
      var scrolls = style.overflowY === "auto" || style.overflowY === "scroll";
      if (scrolls && nodes[i].clientHeight > 80) {
        scroller = nodes[i];
        break;
      }
    }
    if (!scroller) scroller = document.getElementById("pane-side");
    if (!scroller) return;
    var previous = document.querySelectorAll("[data-whatsweb-pad]");
    for (var p = 0; p < previous.length; p++) {
      if (previous[p] !== scroller) delete previous[p].dataset.whatswebPad;
    }
    scroller.dataset.whatswebPad = "1";
  }

  function clearOverlay() {
    if (document.documentElement.dataset.whatswebDrawer) delete document.documentElement.dataset.whatswebDrawer;
    var marked = document.querySelectorAll("[data-whatsweb-drawer]");
    for (var i = 0; i < marked.length; i++) delete marked[i].dataset.whatswebDrawer;
    var bar = document.getElementById("whatsweb-tabbar");
    if (bar) bar.style.visibility = "";
  }

  function markDrawer(drawer, stop) {
    var el = drawer;
    while (el) {
      el.dataset.whatswebDrawer = "1";
      if (!el.parentElement || el === stop) break;
      el = el.parentElement;
    }
  }

  function visibleDrawer(testid) {
    var drawer = document.querySelector("[data-testid='" + testid + "']");
    var box = drawer && drawer.getBoundingClientRect();
    if (!drawer || !box || box.width < 40 || box.height < 80) return null;
    return drawer;
  }

  function closeInfoDrawer() {
    var drawer = visibleDrawer("drawer-right");
    if (!drawer) return false;
    var buttons = drawer.querySelectorAll("button, [role='button']");
    var target = null;
    for (var i = 0; i < buttons.length; i++) {
      var button = buttons[i];
      var icon = iconName(button);
      var label = (button.getAttribute("aria-label") || "").replace(/\s+/g, " ").trim().toLowerCase();
      var closes = controlKind(button) === "back" || label === "close" || icon === "ic-close" || icon === "ic-arrow-back";
      if (!closes) continue;
      var box = button.getBoundingClientRect();
      if (box.width < 8 || box.top > 80) continue;
      if (!target || box.top < target.getBoundingClientRect().top) target = button;
    }
    if (!target) return false;
    target.click();
    return true;
  }

  // A name or photo in the group chat opens that person on top of group info.
  // Closing the person would otherwise reveal a group page the user never asked for.
  var dismissPersonInfo = false;
  var sawContactInfo = false;
  var personObserver = null;

  function personInChat(target) {
    if (!target || !target.closest) return false;
    var hit = target.closest("[data-testid='author'], [data-testid='group-chat-profile-picture']");
    if (!hit) return false;
    var chat = document.querySelector("[data-whatsweb-role='chat']");
    return !!(chat && chat.contains(hit));
  }

  function drawerCloseButton(drawer) {
    var buttons = drawer.querySelectorAll("button, [role='button']");
    var target = null;
    for (var i = 0; i < buttons.length; i++) {
      var button = buttons[i];
      var icon = iconName(button);
      var label = (button.getAttribute("aria-label") || "").replace(/\s+/g, " ").trim().toLowerCase();
      if (icon !== "ic-close" && label !== "close") continue;
      var box = button.getBoundingClientRect();
      if (box.width < 8 || box.top > 80) continue;
      if (!target || box.top < target.getBoundingClientRect().top) target = button;
    }
    return target;
  }

  function disarmPersonInfo() {
    dismissPersonInfo = false;
    sawContactInfo = false;
    if (personObserver) {
      personObserver.disconnect();
      personObserver = null;
    }
  }

  function settlePersonInfo() {
    if (!dismissPersonInfo) return;
    var drawer = visibleDrawer("drawer-right");
    if (!drawer) {
      disarmPersonInfo();
      return;
    }
    var contact = drawer.querySelector("[data-testid='contact-info-header']");
    var group = drawer.querySelector("[data-testid='group-info-header']");
    if (contact) sawContactInfo = true;
    if (group && !contact && !sawContactInfo) {
      disarmPersonInfo();
      return;
    }
    if (!sawContactInfo || !group) return;
    var close = drawerCloseButton(drawer);
    if (!close) return;
    disarmPersonInfo();
    close.click();
  }

  function armPersonInfo() {
    if (dismissPersonInfo) return;
    dismissPersonInfo = true;
    sawContactInfo = false;
    personObserver = new MutationObserver(settlePersonInfo);
    personObserver.observe(document.documentElement, { childList: true, subtree: true });
  }

  function fitOverlay() {
    settlePersonInfo();
    clearOverlay();
    var chat = visibleDrawer("new-chat-drawer");
    if (chat) markDrawer(chat, document.querySelector("[data-testid='drawer-left']"));
    var info = visibleDrawer("chat-info-drawer");
    var right = document.querySelector("[data-testid='drawer-right']");
    if (info && right && right.contains(info)) markDrawer(info, right);
    if (chat || (info && right && right.contains(info))) {
      document.documentElement.dataset.whatswebDrawer = "1";
    }
    reportCover(!!(info && right && right.contains(info)));
  }

  function labelTab(bar, name, text) {
    var slot = bar.querySelector("[data-whatsweb-slot='" + name + "']");
    if (!slot) return;
    var label = slot.querySelector("[data-whatsweb-tab-label]");
    if (!label) {
      label = document.createElement("span");
      label.dataset.whatswebTabLabel = "1";
      slot.appendChild(label);
    }
    if (label.textContent !== text) label.textContent = text;
    if (label.parentNode !== slot || slot.lastElementChild !== label) slot.appendChild(label);
    var button = slot.querySelector("button, [role='button']");
    var selected = button && button.getAttribute("data-navbar-item-selected") === "true";
    if (selected) slot.dataset.whatswebSelected = "1";
    else delete slot.dataset.whatswebSelected;
  }

  function placeBottomNav() {
    var header = navRail();
    if (!header) return false;
    var chats = findKind("chats");
    var status = findKind("status");
    var calls = findKind("calls");
    if (!chats || !status || !calls) return false;
    var badge = chatsBadge(chats);
    var bar = ensureBar();
    var side = document.getElementById("side");
    if (side) {
      var bg = window.getComputedStyle(side).backgroundColor;
      if (bg && bg !== "transparent" && bg !== "rgba(0, 0, 0, 0)") bar.style.backgroundColor = bg;
    }
    park(chats, bar.querySelector("[data-whatsweb-slot='chats']"));
    park(status, bar.querySelector("[data-whatsweb-slot='status']"));
    park(calls, bar.querySelector("[data-whatsweb-slot='calls']"));
    labelTab(bar, "chats", window.__whatswebTabChats || "Chats");
    labelTab(bar, "status", window.__whatswebTabUpdates || "Updates");
    labelTab(bar, "calls", window.__whatswebTabCalls || "Calls");
    var chatsSlot = bar.querySelector("[data-whatsweb-slot='chats']");
    if (badge && chatsSlot) {
      rememberHome(badge);
      trackMoved(badge);
      badge.dataset.whatswebBadge = "1";
      if (badge.parentNode !== chatsSlot) chatsSlot.appendChild(badge);
    }
    var fabs = bar.querySelector("[data-whatsweb-slot='fabs']");
    var meta = findKind("meta");
    var plus = findKind("plus");
    if (plus) park(plus, fabs);
    if (meta && window.__whatswebHideAi) {
      meta.style.setProperty("display", "none", "important");
      meta.dataset.whatswebHideAi = "1";
    } else if (meta) {
      if (meta.dataset.whatswebHideAi) {
        meta.style.display = "";
        delete meta.dataset.whatswebHideAi;
      }
      park(meta, fabs);
      if (plus && meta.parentNode === fabs && plus.parentNode === fabs && meta.nextSibling !== plus) {
        fabs.appendChild(meta);
        fabs.appendChild(plus);
      }
    }
    header.dataset.whatswebNav = "1";
    window.__whatswebNavActive = true;
    collapseChatColumn();
    padList();
    watchNav(header);
    return true;
  }

  function scheduleApply() {
    if (window.__whatswebNavTick) return;
    window.__whatswebNavTick = true;
    window.requestAnimationFrame(function () {
      window.__whatswebNavTick = false;
      apply();
    });
  }

  // A tab's header shows up over the next few frames. Keep correcting until
  // that animation has finished, instead of waiting for the slow poll.
  function followPage() {
    window.__whatswebFollowUntil = performance.now() + 320;
    if (window.__whatswebFollow) return;
    window.__whatswebFollow = true;
    function tick() {
      apply();
      if (performance.now() < window.__whatswebFollowUntil) window.requestAnimationFrame(tick);
      else window.__whatswebFollow = false;
    }
    window.requestAnimationFrame(tick);
  }

  function touchesHeader(node) {
    if (!node || node.nodeType !== 1) return false;
    if (node.tagName === "HEADER" || node.getAttribute("role") === "banner") return true;
    if (node.closest && (node.closest("header") || node.closest("[role='banner']"))) return true;
    if (node.querySelector && node.querySelector("header, [role='banner']")) return true;
    return false;
  }

  function headerMutation(mutations) {
    for (var i = 0; i < mutations.length; i++) {
      if (touchesHeader(mutations[i].target)) return true;
      var added = mutations[i].addedNodes;
      for (var a = 0; a < added.length; a++) {
        if (touchesHeader(added[a])) return true;
      }
    }
    return false;
  }

  function watchNav(header) {
    var observer = window.__whatswebNavObserver;
    if (!observer) {
      observer = new MutationObserver(function () {
        if (!window.__whatswebPhone) return;
        scheduleApply();
      });
      window.__whatswebNavObserver = observer;
    }
    observer.disconnect();
    if (header) observer.observe(header, { childList: true, subtree: true });
    var plus = findKind("plus");
    var plusHome = plus && plus.__whatswebParent;
    if (plusHome && plusHome !== header && header && !header.contains(plusHome)) {
      observer.observe(plusHome, { childList: true });
    }
  }

  function watchPage() {
    var observer = window.__whatswebPageObserver;
    if (!observer) {
      observer = new MutationObserver(function (mutations) {
        if (!window.__whatswebPhone || !headerMutation(mutations)) return;
        scheduleApply();
      });
      window.__whatswebPageObserver = observer;
    }
    var pane = document.getElementById("pane-side") || document.querySelector("[data-whatsweb-role='list']");
    if (!pane || pane === window.__whatswebPageNode) return;
    observer.disconnect();
    window.__whatswebPageNode = pane;
    observer.observe(pane, { childList: true, subtree: true });
  }

  var reportedPane = "";
  var reportedCover = false;
  function reportCover(open) {
    open = !!open;
    if (open === reportedCover) return;
    reportedCover = open;
    postHost({ type: "cover", open: open });
  }

  function reportPane() {
    var pane = window.__whatswebPhone && document.documentElement.dataset.pane === "chat" ? "chat" : "list";
    if (pane === reportedPane) return;
    reportedPane = pane;
    postHost({ type: "pane", pane: pane });
  }

  function layoutPanes() {
    var phone = !!window.__whatswebPhone;
    var pane = document.documentElement.dataset.pane || "list";
    var list = document.querySelector("[data-whatsweb-role='list']");
    var chat = document.querySelector("[data-whatsweb-role='chat']");
    var rail = document.querySelector("[data-whatsweb-role='rail']");
    var shell = document.querySelector("[data-whatsweb-role='shell']");
    document.documentElement.dataset.whatsweb = phone ? "phone" : "tablet";
    if (!list || !chat) {
      reportPane();
      return;
    }
    if (!phone) {
      clearBottomNav();
      clearInline(list);
      clearInline(chat);
      clearInline(rail);
      clearInline(shell);
      reportPane();
      return;
    }
    var showList = pane !== "chat";
    list.style.display = showList ? "" : "none";
    if (chat !== list) chat.style.display = showList ? "none" : "";
    if (rail) rail.style.display = showList ? "" : "none";
    if (showList && rail) {
      list.style.flex = "1 1 auto";
      list.style.flexBasis = "auto";
      list.style.flexShrink = "1";
      list.style.width = "auto";
      list.style.maxWidth = "none";
      list.style.minWidth = "0px";
      var fills = document.querySelectorAll("[data-whatsweb-fill]");
      for (var f = 0; f < fills.length; f++) {
        fills[f].style.width = "100%";
        fills[f].style.maxWidth = "100%";
        fills[f].style.minWidth = "0px";
        fills[f].style.flex = "1 1 auto";
      }
      if (!placeBottomNav()) clearBottomNav();
    } else {
      clearBottomNav();
      [list, chat].forEach(function (el) {
        el.style.width = "100%";
        el.style.maxWidth = "100%";
        el.style.minWidth = "0px";
        el.style.flex = "1 1 auto";
      });
    }
    if (shell) {
      shell.style.minWidth = "0px";
      shell.style.width = "100%";
      shell.style.maxWidth = "100%";
    }
    placeBackRoom();
    reportPane();
  }

  function relax(shell) {
    var el = shell;
    for (var i = 0; i < 6 && el && el !== document.body; i++) {
      el.style.minWidth = "0px";
      el = el.parentElement;
    }
  }

  function mark(found) {
    found.list.dataset.whatswebRole = "list";
    found.chat.dataset.whatswebRole = "chat";
    if (found.shell) found.shell.dataset.whatswebRole = "shell";
    if (found.rail) found.rail.dataset.whatswebRole = "rail";
    (found.fill || []).forEach(function (el) {
      if (el) el.dataset.whatswebFill = "1";
    });
    relax(found.shell || found.list);
    layoutPanes();
  }

  var lastLinked = null;
  function reportSession() {
    var linked = null;
    if (document.getElementById("pane-side")) linked = true;
    else if (document.querySelector("[data-testid='wa-logo']") || document.querySelector("#app canvas")) linked = false;
    if (linked === null || linked === lastLinked) return;
    lastLinked = linked;
    postHost({ type: "session", linked: linked });
  }

  function hideDownloadBanner() {
    var button = document.querySelector(
      "[data-testid='download-native-client-button'], [data-testid='download-native-client-button-inline']"
    );
    if (!button) return;
    var el = button;
    var banner = null;
    while (el && el !== document.body) {
      if (el.querySelector("canvas") || el.querySelector("#link-device-instructions-list")) break;
      banner = el;
      el = el.parentElement;
    }
    if (banner) banner.style.display = "none";
  }

  function placeBackRoom() {
    var marked = document.querySelectorAll("[data-whatsweb-back-room]");
    for (var i = 0; i < marked.length; i++) delete marked[i].dataset.whatswebBackRoom;
    if (!window.__whatswebPhone || document.documentElement.dataset.pane !== "chat") {
      placeChatMenu(null);
      return;
    }
    var header = document.querySelector("[data-testid='conversation-header']");
    var left = header && header.firstElementChild;
    if (left) left.dataset.whatswebBackRoom = "1";
    placeChatMenu(header);
  }

  function placeChatMenu(header) {
    var marked = document.querySelectorAll("[data-whatsweb-chat-menu]");
    for (var i = 0; i < marked.length; i++) delete marked[i].dataset.whatswebChatMenu;
    var searches = document.querySelectorAll("[data-whatsweb-chat-search]");
    for (var s = 0; s < searches.length; s++) delete searches[s].dataset.whatswebChatSearch;
    if (!header || document.documentElement.dataset.pane !== "chat") return;
    var buttons = header.querySelectorAll("button, [role='button']");
    var menu = null;
    var menuRight = -1;
    for (var b = 0; b < buttons.length; b++) {
      var button = buttons[b];
      if (controlKind(button) === "search") button.dataset.whatswebChatSearch = "1";
      var rect = button.getBoundingClientRect();
      if (button.dataset.whatswebChatSearch || rect.width < 8 || rect.right <= menuRight) continue;
      menu = button;
      menuRight = rect.right;
    }
    if (menu) menu.dataset.whatswebChatMenu = "1";
  }

  function iconName(button) {
    if (!button) return "";
    var icon = button.querySelector("[data-icon]");
    if (icon && icon.getAttribute("data-icon")) return icon.getAttribute("data-icon").toLowerCase();
    var title = button.querySelector("svg title");
    return title && title.textContent ? title.textContent.trim().toLowerCase() : "";
  }

  function readTranslateX(el) {
    var match = /translateX\((-?[0-9.]+)px\)/.exec(el.style.transform || "");
    return match ? parseFloat(match[1]) : 0;
  }

  function setShift(el, px) {
    // WhatsApp animates transform. Cancel that so a correction does not lag
    // a frame behind the page change or fight the next correction.
    el.style.transition = "none";
    el.style.transform = "translateX(" + px + "px)";
  }

  function clearShift(el) {
    el.style.transition = "";
    el.style.transform = "";
  }

  function clearStatusHeader() {
    var groups = document.querySelectorAll("[data-whatsweb-status-actions]");
    for (var i = 0; i < groups.length; i++) {
      clearShift(groups[i]);
      delete groups[i].dataset.whatswebStatusActions;
    }
    var pluses = document.querySelectorAll("[data-whatsweb-status-plus]");
    for (var j = 0; j < pluses.length; j++) {
      pluses[j].style.order = "";
      delete pluses[j].dataset.whatswebStatusPlus;
    }
  }

  function findHeaderButton(name) {
    var buttons = document.querySelectorAll("button, [role='button']");
    for (var i = 0; i < buttons.length; i++) {
      var button = buttons[i];
      if (iconName(button) !== name) continue;
      if (button.closest && button.closest("#whatsweb-tabbar")) continue;
      var rect = button.getBoundingClientRect();
      if (rect.width < 8 || rect.top > 80 || rect.bottom < 0) continue;
      return button;
    }
    return null;
  }

  // Status draws Add to the right of its own menu, so Add sits under the app
  // settings gear. The flex child is reordered, then the row is shifted until
  // that menu lines up with the chats menu, which already clears the gear.
  function placeStatusHeader() {
    var plus = findHeaderButton("ic-add-circle");
    var found = null;
    var el = plus && plus.parentElement;
    while (plus && el && el !== document.body) {
      var display = window.getComputedStyle(el).display;
      if ((display === "flex" || display === "inline-flex") && el.children.length >= 2) {
        var menu = null;
        for (var c = 0; c < el.children.length; c++) {
          var button = el.children[c].querySelector("button, [role='button']");
          if (button && iconName(button) === "ic-more-vert") menu = button;
        }
        if (menu && el.contains(plus)) {
          var plusItem = plus;
          while (plusItem.parentElement && plusItem.parentElement !== el) plusItem = plusItem.parentElement;
          found = { group: el, menu: menu, plusItem: plusItem };
          break;
        }
      }
      el = el.parentElement;
    }
    if (!found) {
      clearStatusHeader();
      return;
    }
    var markedGroups = document.querySelectorAll("[data-whatsweb-status-actions]");
    for (var g = 0; g < markedGroups.length; g++) {
      if (markedGroups[g] === found.group) continue;
      clearShift(markedGroups[g]);
      delete markedGroups[g].dataset.whatswebStatusActions;
    }
    var markedPlus = document.querySelectorAll("[data-whatsweb-status-plus]");
    for (var p = 0; p < markedPlus.length; p++) {
      if (markedPlus[p] === found.plusItem) continue;
      markedPlus[p].style.order = "";
      delete markedPlus[p].dataset.whatswebStatusPlus;
    }
    found.plusItem.dataset.whatswebStatusPlus = "1";
    found.plusItem.style.order = "-1";
    found.group.dataset.whatswebStatusActions = "1";
    var target = null;
    var menus = document.querySelectorAll("button, [role='button']");
    for (var m = 0; m < menus.length; m++) {
      var candidate = menus[m];
      if (iconName(candidate) !== "ic-more-vert" || found.group.contains(candidate)) continue;
      if (candidate.closest && candidate.closest("#whatsweb-tabbar")) continue;
      var rect = candidate.getBoundingClientRect();
      if (rect.width < 8 || rect.top > 80 || rect.bottom < 0) continue;
      if (target === null || rect.right > target) target = rect.right;
    }
    if (target === null) target = window.innerWidth - 40;
    var shift = target - found.menu.getBoundingClientRect().right;
    if (Math.abs(shift) < 0.5) return;
    setShift(found.group, readTranslateX(found.group) + shift);
  }

  function headerMenuRight(except) {
    var target = null;
    var menus = document.querySelectorAll("button, [role='button']");
    for (var m = 0; m < menus.length; m++) {
      var candidate = menus[m];
      if (candidate === except || iconName(candidate) !== "ic-more-vert") continue;
      if (candidate.closest && candidate.closest("#whatsweb-tabbar")) continue;
      var rect = candidate.getBoundingClientRect();
      if (rect.width < 8 || rect.top > 80 || rect.bottom < 0) continue;
      if (target === null || rect.right > target) target = rect.right;
    }
    if (target === null) target = window.innerWidth - 40;
    return target;
  }

  // New call and the new-chat keypad sit flush with the header edge, under the
  // app settings gear. Shift each until its right edge matches the chats menu.
  function placeEdgeHeaders() {
    var icons = ["ic-add-call", "ic-dialpad"];
    var keep = [];
    for (var i = 0; i < icons.length; i++) {
      var button = findHeaderButton(icons[i]);
      if (!button) continue;
      keep.push(button);
      button.dataset.whatswebEdgeShift = "1";
      var shift = headerMenuRight(button) - button.getBoundingClientRect().right;
      if (Math.abs(shift) < 0.5) continue;
      setShift(button, readTranslateX(button) + shift);
    }
    var marked = document.querySelectorAll("[data-whatsweb-edge-shift]");
    for (var m = 0; m < marked.length; m++) {
      if (keep.indexOf(marked[m]) !== -1) continue;
      clearShift(marked[m]);
      delete marked[m].dataset.whatswebEdgeShift;
    }
  }

  function releaseHover(el) {
    if (!el || !el.dispatchEvent) return;
    ["pointerout", "pointerleave", "mouseout", "mouseleave"].forEach(function (type) {
      el.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window }));
    });
    if (el.blur) el.blur();
  }

  // Moving a nav button into the bottom bar leaves its tooltip anchor in the old
  // rail. A tap opens that tip and the matching leave never arrives, so it stays
  // up over the page. Close the hover and keep the tip hidden.
  function hideNavTips() {
    var bar = document.getElementById("whatsweb-tabbar");
    var labels = {};
    if (bar) {
      var parked = bar.querySelectorAll("button, [role='button']");
      for (var p = 0; p < parked.length; p++) {
        var label = (parked[p].getAttribute("aria-label") || "").replace(/\s+/g, " ").trim();
        if (label) labels[label] = parked[p];
      }
    }
    var tips = document.querySelectorAll("[role='tooltip']");
    for (var t = 0; t < tips.length; t++) {
      var text = (tips[t].textContent || "").replace(/\s+/g, " ").trim();
      var button = labels[text];
      if (!button) continue;
      tips[t].dataset.whatswebNavtip = "1";
      if (tips[t].__whatswebTipClosed) continue;
      tips[t].__whatswebTipClosed = true;
      var home = button.__whatswebParent;
      var described = home && home.closest("[aria-describedby]");
      releaseHover(described || home);
      releaseHover(button);
    }
  }

  function placeQuietRoom() {
    var marked = document.querySelectorAll("[data-whatsweb-quiet-room]");
    for (var i = 0; i < marked.length; i++) delete marked[i].dataset.whatswebQuietRoom;
    if (!window.__whatswebQuiet) return;
    var buttons = document.querySelectorAll("button, [role='button']");
    for (var b = 0; b < buttons.length; b++) {
      var button = buttons[b];
      if (button.closest && button.closest("#whatsweb-tabbar")) continue;
      var rect = button.getBoundingClientRect();
      if (rect.width < 8 || rect.top > 72 || rect.bottom < 0) continue;
      if (rect.right < window.innerWidth - 88) continue;
      var row = button.parentElement;
      while (row && row !== document.body) {
        var parentRect = row.getBoundingClientRect();
        if (parentRect.width > 200 && parentRect.height > 0 && parentRect.height < 96 && parentRect.top < 80) break;
        row = row.parentElement;
      }
      if (!row || row === document.body) continue;
      row.dataset.whatswebQuietRoom = "1";
    }
  }

  function apply() {
    hideDownloadBanner();
    placeQuietRoom();
    placeStatusHeader();
    placeEdgeHeaders();
    hideNavTips();
    placeBackRoom();
    var found = companionPanes();
    if (!found) {
      if (document.getElementById("pane-side")) {
        reportSession();
        reportCover(false);
        return;
      }
      clearBottomNav();
      clearMarks();
      delete document.documentElement.dataset.whatsweb;
      fitLogin();
      fitLoginWidth();
      alignLoginTop();
      reportSession();
      reportPane();
      reportCover(false);
      return;
    }
    clearLoginFit();
    var marked = document.querySelector("[data-whatsweb-role='list']");
    if (marked !== found.list || !document.querySelector("[data-whatsweb-role='chat']")) {
      clearMarks();
      mark(found);
    } else {
      layoutPanes();
    }
    reportSession();
    if (window.__whatswebPhone) {
      fitOverlay();
      watchPage();
    } else clearOverlay();
    if (!window.__whatswebPhone) reportCover(false);
  }

  function revealChat() {
    document.documentElement.dataset.opening = "1";
    var chat = document.querySelector("[data-whatsweb-role='chat']");
    var list = document.querySelector("[data-whatsweb-role='list']");
    if (chat && chat !== list) chat.style.display = "";
  }

  function openChat() {
    delete document.documentElement.dataset.opening;
    document.documentElement.dataset.pane = "chat";
    layoutPanes();
    window.dispatchEvent(new Event("resize"));
    reportPane();
  }

  document.addEventListener("pointerup", function (event) {
    if (!window.__whatswebPhone) return;
    var target = event.target;
    var button = target && target.closest && target.closest("#whatsweb-tabbar button, #whatsweb-tabbar [role='button']");
    if (button) {
      releaseHover(button);
      followPage();
    }
    window.setTimeout(hideNavTips, 0);
  }, true);

  document.addEventListener("pointerdown", function (event) {
    if (!window.__whatswebPhone) return;
    var target = event.target;
    if (!target || !target.closest) return;
    var list = document.querySelector("[data-whatsweb-role='list']");
    var item = target.closest("[role='listitem'], [role='row'], [role='gridcell']");
    if (list && item && list.contains(item)) revealChat();
  }, true);

  document.addEventListener("click", function (event) {
    if (!window.__whatswebPhone) return;
    if (personInChat(event.target) && !visibleDrawer("drawer-right")) armPersonInfo();
    window.setTimeout(function () {
      if (window.__whatswebPhone) fitOverlay();
    }, 300);
    var target = event.target;
    if (!target || !target.closest) return;
    var list = document.querySelector("[data-whatsweb-role='list']");
    var chat = document.querySelector("[data-whatsweb-role='chat']");
    var item = target.closest("[role='listitem'], [role='row'], [role='gridcell']");
    if (list && item && list.contains(item)) {
      window.setTimeout(openChat, 60);
      return;
    }
    if (document.documentElement.dataset.opening) delete document.documentElement.dataset.opening;
    var button = target.closest("button, [role='button']");
    if (chat && button && chat.contains(button)) {
      if (controlKind(button) === "back" && window.whatswebBack) window.whatswebBack();
    }
  }, true);

  window.whatswebSetHideAi = function (hide) {
    window.__whatswebHideAi = !!hide;
    apply();
  };

  window.whatswebSetQuiet = function (quiet) {
    window.__whatswebQuiet = !!quiet;
    placeQuietRoom();
  };

  window.whatswebSetPhone = function (phone) {
    window.__whatswebPhone = !!phone;
    if (!phone) {
      document.documentElement.dataset.pane = "both";
    } else if (!document.documentElement.dataset.pane || document.documentElement.dataset.pane === "both") {
      document.documentElement.dataset.pane = "list";
    }
    apply();
  };

  window.whatswebBack = function () {
    if (!window.__whatswebPhone) return false;
    delete document.documentElement.dataset.opening;
    if (closeInfoDrawer()) return true;
    if (document.documentElement.dataset.pane === "chat") {
      document.documentElement.dataset.pane = "list";
      layoutPanes();
      window.dispatchEvent(new Event("resize"));
      reportPane();
      return true;
    }
    reportPane();
    return false;
  };

  apply();
  [300, 800, 1600].forEach(function (ms) { window.setTimeout(apply, ms); });
  window.setInterval(apply, 1500);
  return true;
})();
