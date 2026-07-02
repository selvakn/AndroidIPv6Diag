package web

import _ "embed"

//go:embed dashboard.html
var DashboardHTML []byte

//go:embed browser_diagnostics.html
var BrowserDiagnosticsHTML []byte
