package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestBrowserDiagnosticsPageHandlerMethod(t *testing.T) {
	h := &BrowserDiagnosticsPageHandler{}
	req := httptest.NewRequest(http.MethodPost, "/browser-diagnostics", nil)
	rr := httptest.NewRecorder()

	h.ServeHTTP(rr, req)

	if rr.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405, got %d", rr.Code)
	}
}

func TestBrowserDiagnosticsConfigHandlerMethod(t *testing.T) {
	h := &BrowserDiagnosticsConfigHandler{}
	req := httptest.NewRequest(http.MethodPost, "/browser-diagnostics/config", nil)
	rr := httptest.NewRecorder()

	h.ServeHTTP(rr, req)

	if rr.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected 405, got %d", rr.Code)
	}
}

func TestBrowserDiagnosticsConfigHandlerPayload(t *testing.T) {
	t.Setenv("BROWSER_DIAG_ALLOW_CUSTOM_TARGETS", "true")
	t.Setenv("BROWSER_DIAG_PER_TEST_TIMEOUT_MS", "20000")
	t.Setenv("TURN_ENABLED", "true")
	t.Setenv("TURN_CREDENTIALS_TOKEN", "")
	t.Setenv("BROWSER_DIAG_HTTP_TARGET", "http://localhost:8080/diag")

	h := &BrowserDiagnosticsConfigHandler{}
	req := httptest.NewRequest(http.MethodGet, "/browser-diagnostics/config", nil)
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rr.Code)
	}

	var payload browserDiagConfigResponse
	if err := json.Unmarshal(rr.Body.Bytes(), &payload); err != nil {
		t.Fatalf("invalid payload: %v", err)
	}
	if payload.PerTestTimeoutMS != 20000 {
		t.Fatalf("expected timeout 20000, got %d", payload.PerTestTimeoutMS)
	}
	if payload.TurnCredentialMode != "tokenless_endpoint" {
		t.Fatalf("expected tokenless_endpoint mode, got %s", payload.TurnCredentialMode)
	}
	if len(payload.DefaultTargets) == 0 {
		t.Fatalf("expected default targets")
	}
	if payload.DefaultTargets[0].Value != "http://localhost:8080/diag" {
		t.Fatalf("unexpected default HTTP target: %s", payload.DefaultTargets[0].Value)
	}
}
