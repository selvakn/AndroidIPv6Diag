package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/lenovo/mesh/ipv6diag-server/internal/handler"
	"github.com/lenovo/mesh/ipv6diag-server/internal/listener"
)

var version = "dev"

func main() {
	httpAddr := flag.String("http-addr", "0.0.0.0:80", "IPv4 HTTP listen address")
	http6Addr := flag.String("http6-addr", "[::]:80", "IPv6 HTTP listen address")
	httpsAddr := flag.String("https-addr", "0.0.0.0:443", "IPv4 HTTPS listen address")
	https6Addr := flag.String("https6-addr", "[::]:443", "IPv6 HTTPS listen address")
	certFile := flag.String("cert", "", "Path to TLS certificate file (PEM)")
	keyFile := flag.String("key", "", "Path to TLS private key file (PEM)")
	showVersion := flag.Bool("version", false, "Print version and exit")
	flag.Parse()

	if *showVersion {
		fmt.Println(version)
		os.Exit(0)
	}

	listeners, err := listener.Create(*httpAddr, *http6Addr, *httpsAddr, *https6Addr, *certFile, *keyFile)
	if err != nil {
		log.Fatalf("Failed to create listeners: %v", err)
	}
	defer listeners.CloseAll()

	httpMux := http.NewServeMux()
	httpMux.Handle("/diag", &handler.DiagHandler{IsTLS: false})
	httpMux.Handle("/health", &handler.HealthHandler{})

	tlsMux := http.NewServeMux()
	tlsMux.Handle("/diag", &handler.DiagHandler{IsTLS: true})
	tlsMux.Handle("/health", &handler.HealthHandler{})

	errCh := make(chan error, 4)

	go func() {
		log.Printf("IPv4 HTTP listening on %s", listeners.IPv4HTTP.Addr())
		errCh <- (&http.Server{Handler: httpMux}).Serve(listeners.IPv4HTTP)
	}()

	go func() {
		log.Printf("IPv6 HTTP listening on %s", listeners.IPv6HTTP.Addr())
		errCh <- (&http.Server{Handler: httpMux}).Serve(listeners.IPv6HTTP)
	}()

	if listeners.IPv4HTTPS != nil {
		go func() {
			log.Printf("IPv4 HTTPS listening on %s", listeners.IPv4HTTPS.Addr())
			errCh <- (&http.Server{Handler: tlsMux}).Serve(listeners.IPv4HTTPS)
		}()
	}

	if listeners.IPv6HTTPS != nil {
		go func() {
			log.Printf("IPv6 HTTPS listening on %s", listeners.IPv6HTTPS.Addr())
			errCh <- (&http.Server{Handler: tlsMux}).Serve(listeners.IPv6HTTPS)
		}()
	}

	if *certFile == "" || *keyFile == "" {
		log.Println("TLS cert/key not provided — HTTPS listeners disabled (HTTP only)")
	}

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	select {
	case sig := <-quit:
		log.Printf("Received signal %s, shutting down...", sig)
	case err := <-errCh:
		log.Printf("Listener error: %v", err)
	}

	_, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	listeners.CloseAll()
	log.Println("Server stopped.")
}
