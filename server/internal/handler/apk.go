package handler

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const maxAPKBytes = 200 << 20 // 200 MiB ceiling

// APKHandler stores and serves the Android release APK.
//
//   - POST /upload-apk   (Bearer token) — CI uploads a freshly built APK
//   - GET  /download/apk — public download of the latest APK
//   - GET  /apk-info     — JSON metadata (availability, version, size, sha256)
//
// The APK and its metadata live on the persistent data volume so they survive
// restarts. Uploads are written atomically (temp file + rename).
type APKHandler struct {
	Dir         string // directory on the data volume to store the APK + metadata
	UploadToken string // shared secret; uploads are rejected unless this is set and matches
}

type apkMeta struct {
	Available  bool   `json:"available"`
	Version    string `json:"version"`
	SizeBytes  int64  `json:"size_bytes"`
	SHA256     string `json:"sha256"`
	UploadedAt string `json:"uploaded_at"`
	Filename   string `json:"filename"`
}

func (h *APKHandler) apkPath() string  { return filepath.Join(h.Dir, "app-release.apk") }
func (h *APKHandler) metaPath() string { return filepath.Join(h.Dir, "apk-meta.json") }

func (h *APKHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.URL.Path == "/upload-apk" && r.Method == http.MethodPost:
		h.handleUpload(w, r)
	case r.URL.Path == "/download/apk" && r.Method == http.MethodGet:
		h.handleDownload(w, r)
	case r.URL.Path == "/apk-info" && r.Method == http.MethodGet:
		h.handleInfo(w, r)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (h *APKHandler) handleUpload(w http.ResponseWriter, r *http.Request) {
	if h.UploadToken == "" {
		writeError(w, http.StatusForbidden, "APK upload is disabled (no APK_UPLOAD_TOKEN configured)")
		return
	}
	provided := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if subtle.ConstantTimeCompare([]byte(provided), []byte(h.UploadToken)) != 1 {
		writeError(w, http.StatusUnauthorized, "invalid or missing upload token")
		return
	}

	if err := os.MkdirAll(h.Dir, 0o755); err != nil {
		writeError(w, http.StatusInternalServerError, "could not prepare storage directory")
		return
	}

	tmp, err := os.CreateTemp(h.Dir, "apk-upload-*.tmp")
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create temp file")
		return
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName) // no-op after a successful rename

	hash := sha256.New()
	limited := io.LimitReader(r.Body, maxAPKBytes+1)
	n, err := io.Copy(io.MultiWriter(tmp, hash), limited)
	tmp.Close()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "failed to read upload body")
		return
	}
	if n > maxAPKBytes {
		writeError(w, http.StatusRequestEntityTooLarge, "APK exceeds size limit")
		return
	}
	if n == 0 {
		writeError(w, http.StatusBadRequest, "empty upload body")
		return
	}

	if err := os.Rename(tmpName, h.apkPath()); err != nil {
		writeError(w, http.StatusInternalServerError, "could not store APK")
		return
	}

	meta := apkMeta{
		Available:  true,
		Version:    r.Header.Get("X-App-Version"),
		SizeBytes:  n,
		SHA256:     hex.EncodeToString(hash.Sum(nil)),
		UploadedAt: time.Now().UTC().Format(time.RFC3339),
		Filename:   "ipv6diag.apk",
	}
	if metaBytes, err := json.Marshal(meta); err == nil {
		os.WriteFile(h.metaPath(), metaBytes, 0o644)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(meta)
}

func (h *APKHandler) handleDownload(w http.ResponseWriter, r *http.Request) {
	f, err := os.Open(h.apkPath())
	if err != nil {
		writeError(w, http.StatusNotFound, "no APK has been published yet")
		return
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not stat APK")
		return
	}
	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	w.Header().Set("Content-Disposition", `attachment; filename="ipv6diag.apk"`)
	http.ServeContent(w, r, "ipv6diag.apk", info.ModTime(), f)
}

func (h *APKHandler) handleInfo(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	data, err := os.ReadFile(h.metaPath())
	if err != nil {
		json.NewEncoder(w).Encode(apkMeta{Available: false})
		return
	}
	w.Write(data)
}
