// Foundora: small progressive enhancements. Every page works without this file; it only makes things smoother.
(function () {
  "use strict";

  const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
  const MAX_PHOTO_EDGE = 1600;
  const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];
  const wideScreen = window.matchMedia("(min-width: 768px)");

  // ---------- Report dialog: a pop-up on wide screens; on phones the link opens the full /report page ----------
  document.addEventListener("click", (event) => {
    const opener = event.target.closest("[data-open-dialog]");
    if (opener) {
      const dialog = document.getElementById(opener.dataset.openDialog);
      if (dialog && typeof dialog.showModal === "function" && wideScreen.matches) {
        event.preventDefault();
        const type = opener.dataset.type;
        if (type) {
          const radio = dialog.querySelector(`input[name="type"][value="${type}"]`);
          if (radio) radio.checked = true;
        }
        dialog.showModal();
        const first = dialog.querySelector("input:checked, input, select, textarea");
        if (first) first.focus();
      }
      return;
    }
    const closer = event.target.closest("[data-close-dialog]");
    if (closer) {
      const dialog = closer.closest("dialog");
      if (dialog) {
        event.preventDefault();
        dialog.close();
      }
    }
  });

  // Clicking the dimmed backdrop closes a dialog.
  document.addEventListener("click", (event) => {
    if (event.target instanceof HTMLDialogElement) {
      const r = event.target.getBoundingClientRect();
      const inside = event.clientX >= r.left && event.clientX <= r.right && event.clientY >= r.top && event.clientY <= r.bottom;
      if (!inside) event.target.close();
    }
  });

  // ---------- Busy state on submit, so a slow upload doesn't get submitted twice ----------
  document.addEventListener("submit", (event) => {
    const form = event.target;
    if (!form.matches("[data-loading]")) return;
    const button = event.submitter || form.querySelector('button[type="submit"]');
    if (!button) return;
    if (button.getAttribute("aria-busy") === "true") {
      event.preventDefault();
      return;
    }
    button.setAttribute("aria-busy", "true");
    if (form.matches("[data-report-form]")) button.textContent = "Sending…";
    if (form.matches("[data-comment-form]")) button.textContent = "Posting…";
  });

  // ---------- Photo picker: preview, check type and size, shrink big photos before upload ----------
  function setupPhotoPicker(form) {
    const input = form.querySelector("[data-photo-input]");
    if (!input) return;
    const preview = form.querySelector("[data-photo-preview]");
    const previewImg = preview.querySelector("img");
    const clear = form.querySelector("[data-photo-clear]");
    const error = form.querySelector("[data-photo-error]");
    const buttonLabel = form.querySelector("[data-photo-button]");
    let previewUrl = null;

    function showError(message) {
      error.textContent = message;
      error.hidden = !message;
      if (message) {
        input.setAttribute("aria-invalid", "true");
        input.setAttribute("aria-describedby", error.id);
      } else {
        input.removeAttribute("aria-invalid");
      }
    }

    function showPreview(file) {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      previewUrl = file ? URL.createObjectURL(file) : null;
      previewImg.src = previewUrl || "";
      preview.classList.toggle("has-photo", Boolean(file));
      clear.hidden = !file;
      buttonLabel.textContent = file ? "Choose a different photo" : (form.matches("[data-comment-form]") ? "Add a photo" : "Choose a photo");
      form.dispatchEvent(new CustomEvent("photo-preview", { detail: { url: previewUrl } }));
    }

    // Phones: "Take a photo" opens the camera directly; "Choose a photo" opens the gallery.
    const camera = form.querySelector("[data-photo-camera]");
    const choose = form.querySelector("[data-photo-choose]");
    if (camera && window.matchMedia("(pointer: coarse)").matches) {
      camera.hidden = false;
      camera.addEventListener("click", () => {
        input.setAttribute("capture", "environment");
        input.click();
      });
      if (choose) choose.addEventListener("click", () => input.removeAttribute("capture"));
    }

    function setFile(file) {
      try {
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
        return true;
      } catch (e) {
        return false; // older browsers: keep the original file, the server resizes it anyway
      }
    }

    async function shrink(file) {
      if (!("createImageBitmap" in window)) return file;
      const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
      const scale = Math.min(1, MAX_PHOTO_EDGE / Math.max(bitmap.width, bitmap.height));
      if (scale === 1 && file.size <= 1024 * 1024) {
        bitmap.close();
        return file;
      }
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#fff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      bitmap.close();
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
      if (!blob) return file;
      const name = file.name.replace(/\.[^.]+$/, "") + ".jpg";
      return new File([blob], name, { type: "image/jpeg", lastModified: Date.now() });
    }

    input.addEventListener("change", async () => {
      const file = input.files && input.files[0];
      showError("");
      if (!file) {
        showPreview(null);
        return;
      }
      if (!PHOTO_TYPES.includes(file.type)) {
        input.value = "";
        showPreview(null);
        showError("The photo must be a JPEG, PNG or WebP image.");
        return;
      }
      let chosen = file;
      try {
        chosen = await shrink(file);
        if (chosen !== file) setFile(chosen);
      } catch (e) {
        chosen = file;
      }
      if (chosen.size > MAX_PHOTO_BYTES) {
        input.value = "";
        showPreview(null);
        showError("The photo must be 5 MB or smaller. Please choose a smaller photo.");
        return;
      }
      showPreview(chosen);
    });

    clear.addEventListener("click", () => {
      input.value = "";
      showPreview(null);
      showError("");
      input.focus();
    });
  }

  // ---------- Suggestions as you type (answered by the server's prefix tries) ----------
  function debounce(fn, ms) {
    let timer;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), ms);
    };
  }

  function fillDatalist(list, values) {
    list.replaceChildren(...values.map((value) => {
      const option = document.createElement("option");
      option.value = value;
      return option;
    }));
  }

  function setupSuggestions(input, endpoint, completeLastWord) {
    const list = document.getElementById(input.getAttribute("list"));
    if (!list) return;
    let lastQuery = null;
    const update = debounce(async () => {
      const value = input.value;
      const words = value.split(/\s+/);
      const prefix = completeLastWord ? words[words.length - 1] : value;
      if (completeLastWord && !prefix) return;
      if (prefix === lastQuery) return;
      lastQuery = prefix;
      try {
        const response = await fetch(`${endpoint}?q=${encodeURIComponent(prefix)}`, { headers: { Accept: "application/json" } });
        if (!response.ok) return;
        const suggestions = await response.json();
        const head = completeLastWord ? words.slice(0, -1).join(" ") : "";
        fillDatalist(list, suggestions.map((s) => (head ? `${head} ${s}` : s)));
      } catch (e) {
        // suggestions are optional
      }
    }, 150);
    input.addEventListener("input", update);
    input.addEventListener("focus", update);
  }

  // ---------- Claim form: registered users matching what the admin types (name or email) ----------
  function setupPeopleSuggestions(input) {
    const list = document.getElementById(input.getAttribute("list"));
    if (!list) return;
    let lastQuery = null;
    const update = debounce(async () => {
      const q = input.value.trim();
      if (q.length < 2 || q === lastQuery) return;
      lastQuery = q;
      try {
        const response = await fetch(`/admin/api/people?q=${encodeURIComponent(q)}`, { headers: { Accept: "application/json" } });
        if (!response.ok) return;
        const people = await response.json();
        list.replaceChildren(...people.map((person) => {
          const option = document.createElement("option");
          option.value = person.email;
          option.label = person.label;
          option.textContent = person.label;
          return option;
        }));
      } catch (e) {
        // suggestions are optional; the admin can still type the email
      }
    }, 200);
    input.addEventListener("input", update);
  }

  // ---------- Comment box: characters left ----------
  function setupCharCount(textarea) {
    const counter = textarea.closest(".field").querySelector("[data-char-count]");
    const max = Number(textarea.getAttribute("maxlength"));
    if (!counter || !max) return;
    const update = () => {
      const left = max - textarea.value.length;
      counter.textContent = left === max ? `Up to ${max} characters.` : `${left} characters left.`;
    };
    textarea.addEventListener("input", update);
    update();
  }

  // ---------- Profile: show the "Other" program box only when Other is chosen ----------
  function setupOtherToggle(select) {
    const field = document.getElementById(select.dataset.otherToggle);
    if (!field) return;
    const input = field.querySelector("input");
    const sync = () => {
      const other = select.value === "Other";
      field.hidden = !other;
      if (input) input.required = other;
    };
    select.addEventListener("change", sync);
    sync();
  }

  // ---------- "Load more": fetch the next page of posts and append it; also loads as you scroll ----------
  function setupLoadMore(container) {
    const link = container.querySelector("a");
    let loading = false;

    async function load(event) {
      if (event) event.preventDefault();
      if (loading) return;
      loading = true;
      container.setAttribute("aria-busy", "true");
      link.textContent = "Loading…";
      try {
        const response = await fetch(container.dataset.next, { headers: { Accept: "text/html" } });
        if (!response.ok) throw new Error(String(response.status));
        const template = document.createElement("template");
        template.innerHTML = await response.text();
        const added = template.content.querySelectorAll("article[data-post]").length;
        container.replaceWith(template.content);
        announce(`Loaded ${added} more ${added === 1 ? "item" : "items"}.`);
        document.querySelectorAll("[data-load-more]").forEach(setupLoadMore);
      } catch (e) {
        loading = false;
        container.removeAttribute("aria-busy");
        link.textContent = "Couldn't load more. Try again";
      }
    }

    link.addEventListener("click", load);
    if ("IntersectionObserver" in window && !container.dataset.observed) {
      container.dataset.observed = "true";
      const observer = new IntersectionObserver((entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          observer.disconnect();
          load();
        }
      }, { rootMargin: "400px 0px" });
      observer.observe(container);
    }
  }

  // ---------- Report page: the item's ID card fills in as the form is completed ----------
  function setupReportPreview(form) {
    const preview = document.querySelector("[data-report-preview]");
    if (!preview) return;
    const card = preview.querySelector("[data-preview-card]");
    const field = (name) => form.querySelector(`[data-preview="${name}"]`);
    const out = (name) => preview.querySelector(`[data-preview-${name}]`);
    const dateFormat = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });

    function setText(el, value) {
      const empty = !value;
      el.textContent = empty ? el.dataset.empty : value;
      el.toggleAttribute("data-empty-shown", empty);
    }

    function update() {
      const type = form.querySelector('input[name="type"]:checked');
      const lost = Boolean(type) && type.value === "LOST";
      card.classList.toggle("is-unset", !type);
      card.classList.toggle("is-lost", lost);
      card.classList.toggle("is-found", Boolean(type) && !lost);
      out("type").textContent = !type ? "Lost or found?" : lost ? "Lost item" : "Found item";
      out("date-label").textContent = !type ? "Date" : lost ? "Lost" : "Found";
      setText(out("name"), field("name").value.trim());
      const category = field("category");
      setText(out("category"), category.value ? category.options[category.selectedIndex].text : "");
      // No photo: the board shows the category's glyph, so the preview does too.
      preview.querySelectorAll("[data-preview-glyph]").forEach((glyph) => {
        glyph.hidden = glyph.dataset.previewGlyph !== category.value;
      });
      const date = field("date").value;
      setText(out("date"), date ? dateFormat.format(new Date(date + "T00:00")) : "");
    }

    form.addEventListener("input", update);
    form.addEventListener("change", update);
    form.addEventListener("photo-preview", (event) => {
      const img = out("photo");
      const url = event.detail.url;
      img.hidden = !url;
      img.src = url || "";
      out("photo-empty").hidden = Boolean(url);
    });
    preview.hidden = false;
    update();
  }

  // ---------- Filter forms that apply as soon as a choice is made (the Apply button stays for no-JS) ----------
  function setupAutosubmit(form) {
    const button = form.querySelector("[data-autosubmit-button]");
    if (button) button.hidden = true;
    form.querySelectorAll("select").forEach((select) =>
      select.addEventListener("change", () => form.requestSubmit ? form.requestSubmit() : form.submit()));
  }

  let liveRegion;
  function announce(message) {
    if (!liveRegion) {
      liveRegion = document.createElement("p");
      liveRegion.className = "visually-hidden";
      liveRegion.setAttribute("role", "status");
      document.body.appendChild(liveRegion);
    }
    liveRegion.textContent = message;
  }

  // ---------- Tap a photo to see it full size ----------
  let lightbox;
  function openLightbox(href, caption) {
    if (typeof HTMLDialogElement !== "function") return false;
    if (!lightbox) {
      lightbox = document.createElement("dialog");
      lightbox.className = "lightbox";
      lightbox.setAttribute("aria-label", "Photo");
      lightbox.innerHTML =
        '<div class="dialog-head"><h2></h2><button class="btn btn-ghost btn-small" type="button" data-close-dialog>Close</button></div><img alt="">';
      document.body.appendChild(lightbox);
    }
    lightbox.querySelector("h2").textContent = caption || "Photo";
    const img = lightbox.querySelector("img");
    img.src = href;
    img.alt = caption ? `Photo of ${caption}` : "Photo";
    lightbox.showModal();
    return true;
  }

  document.addEventListener("click", (event) => {
    const link = event.target.closest("a[data-lightbox]");
    if (link && openLightbox(link.href, link.dataset.caption)) event.preventDefault();
  });

  // ---------- Date ranges: "to" can't be before "from" ----------
  function setupDateRange(scope) {
    const from = scope.querySelector("[data-range-from]");
    const to = scope.querySelector("[data-range-to]");
    if (!from || !to) return;
    const sync = () => {
      if (from.value) to.min = from.value;
      else to.removeAttribute("min");
    };
    from.addEventListener("change", sync);
    sync();
  }

  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("form[data-report-form], form[data-comment-form]").forEach(setupPhotoPicker);
    document.querySelectorAll(".report-sheet form[data-report-form]").forEach(setupReportPreview);
    document.querySelectorAll("input[data-suggest-people]").forEach(setupPeopleSuggestions);
    document.querySelectorAll("textarea[data-count-chars]").forEach(setupCharCount);
    document.querySelectorAll("select[data-other-toggle]").forEach(setupOtherToggle);
    document.querySelectorAll("input[data-suggest-search]").forEach((input) =>
      setupSuggestions(input, "/api/suggest/search", true));
    document.querySelectorAll("[data-load-more]").forEach(setupLoadMore);
    document.querySelectorAll("form").forEach(setupDateRange);
    document.querySelectorAll("form[data-autosubmit]").forEach(setupAutosubmit);
  });
})();
