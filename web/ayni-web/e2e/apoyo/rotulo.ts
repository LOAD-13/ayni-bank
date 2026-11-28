import type { Page } from "@playwright/test";

/**
 * Rótulo que explica en pantalla qué se está probando, para que el video de la ejecución se
 * entienda sin narración. Solo aparece con `ROTULOS=1`, que activa quien graba los videos: en
 * la CI la pantalla queda exactamente como la ve el cliente. Va fuera del árbol accesible
 * (`aria-hidden`) para no alterar lo que mide axe.
 */
export async function rotular(page: Page, titulo: string, detalle = "") {
  if (process.env.ROTULOS !== "1") return;
  await page.evaluate(
    ([t, d]) => {
      document.getElementById("ayni-rotulo")?.remove();
      const caja = document.createElement("div");
      caja.id = "ayni-rotulo";
      caja.setAttribute("aria-hidden", "true");
      caja.style.cssText =
        "position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:2147483647;" +
        "max-width:80%;padding:12px 20px;border-radius:12px;background:rgba(6,30,54,.92);" +
        "color:#fff;font:600 16px/1.4 system-ui,sans-serif;box-shadow:0 8px 30px rgba(0,0,0,.35);" +
        "pointer-events:none;animation:ayniRotulo .35s ease-out";
      caja.innerHTML = `<div>${t}</div>${d ? `<div style="font-weight:400;font-size:14px;opacity:.85">${d}</div>` : ""}`;
      if (!document.getElementById("ayni-rotulo-css")) {
        const css = document.createElement("style");
        css.id = "ayni-rotulo-css";
        css.textContent =
          "@keyframes ayniRotulo{from{opacity:0;transform:translate(-50%,10px)}to{opacity:1;transform:translate(-50%,0)}}";
        document.head.appendChild(css);
      }
      document.body.appendChild(caja);
    },
    [titulo, detalle],
  );
  // Que el rótulo se alcance a leer en el video antes de la siguiente acción.
  await page.waitForTimeout(2500);
}
