(() => {
    "use strict";

    async function initializeDemo() {
        const response = await fetch("/api/demo-mode", {
            credentials: "same-origin",
            cache: "no-store",
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            return;
        }

        const settings = await response.json();

        if (
            settings.enabled !== true ||
            document.getElementById("eltro-demo-banner")
        ) {
            return;
        }

        const stylesheet = document.createElement("link");
        stylesheet.rel = "stylesheet";
        stylesheet.href = "/eltro-demo.css";
        document.head.append(stylesheet);

        document.body.classList.add("eltro-demo");

        const banner = document.createElement("div");
        banner.id = "eltro-demo-banner";
        banner.textContent =
            "WERSJA DEMONSTRACYJNA · Dane przykładowe";

        const watermark = document.createElement("div");
        watermark.id = "eltro-demo-watermark";
        watermark.setAttribute("aria-hidden", "true");

        for (const text of [
            "WERSJA DEMONSTRACYJNA",
            "Konrad Papiernik",
            "695 187 333 · kppapiernik@gmail.com"
        ]) {
            const line = document.createElement("div");
            line.textContent = text;
            watermark.append(line);
        }

        const printmark = document.createElement("div");
        printmark.id = "eltro-demo-printmark";
        printmark.textContent =
            "DEMO — DOKUMENT DEMONSTRACYJNY";
        printmark.setAttribute("aria-hidden", "true");

        document.body.append(banner, watermark, printmark);
    }

    function start() {
        initializeDemo().catch(error => {
            console.warn(
                "Nie udało się wczytać oznaczenia demo.",
                error
            );
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener(
            "DOMContentLoaded",
            start,
            { once: true }
        );
    } else {
        start();
    }
})();