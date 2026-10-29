import { StrictMode } from "react"
import { createRoot } from "react-dom/client"

import "@/index.css"
import "@fontsource/barlow/400.css"
import "@fontsource/barlow/500.css"
import "@fontsource/barlow/600.css"
import "@fontsource/barlow/700.css"
import "@fontsource/barlow-condensed/500.css"
import "@fontsource/barlow-condensed/600.css"
import "@fontsource/barlow-condensed/700.css"
import "@fontsource/jetbrains-mono/400.css"
import "@fontsource/jetbrains-mono/600.css"
import "@/styles/finemp-theme.css"
import "@/styles/finemp-overrides.css"
import { AppProviders } from "./app/providers/app-providers"
import { AppRouter } from "./app/router/app-router"

createRoot(
  document.getElementById("root")!,
).render(
  <StrictMode>
    <AppProviders>
      <AppRouter />
    </AppProviders>
  </StrictMode>,
)