package listener

import (
	"crypto/tls"
	"fmt"
	"net"
)

// Listeners holds all four TCP listeners: IPv4/IPv6 × HTTP/HTTPS.
type Listeners struct {
	IPv4HTTP  net.Listener
	IPv6HTTP  net.Listener
	IPv4HTTPS net.Listener
	IPv6HTTPS net.Listener
	TLSConfig *tls.Config
}

// Create opens four separate listeners. HTTPS listeners are nil when certFile or keyFile is empty.
// Separate IPv4 (0.0.0.0) and IPv6 ([::]) listeners avoid IPv4-mapped address ambiguity that
// arises with a single dual-stack [::] socket when net.ipv6only=0.
func Create(httpAddr, http6Addr, httpsAddr, https6Addr, certFile, keyFile string) (*Listeners, error) {
	l := &Listeners{}

	var err error
	l.IPv4HTTP, err = net.Listen("tcp4", httpAddr)
	if err != nil {
		return nil, fmt.Errorf("IPv4 HTTP listener on %s: %w", httpAddr, err)
	}

	l.IPv6HTTP, err = net.Listen("tcp6", http6Addr)
	if err != nil {
		l.IPv4HTTP.Close()
		return nil, fmt.Errorf("IPv6 HTTP listener on %s: %w", http6Addr, err)
	}

	if certFile != "" && keyFile != "" {
		cert, err := tls.LoadX509KeyPair(certFile, keyFile)
		if err != nil {
			l.IPv4HTTP.Close()
			l.IPv6HTTP.Close()
			return nil, fmt.Errorf("loading TLS certificate: %w", err)
		}
		l.TLSConfig = &tls.Config{
			Certificates: []tls.Certificate{cert},
			MinVersion:   tls.VersionTLS12,
		}

		l.IPv4HTTPS, err = tls.Listen("tcp4", httpsAddr, l.TLSConfig)
		if err != nil {
			l.IPv4HTTP.Close()
			l.IPv6HTTP.Close()
			return nil, fmt.Errorf("IPv4 HTTPS listener on %s: %w", httpsAddr, err)
		}

		l.IPv6HTTPS, err = tls.Listen("tcp6", https6Addr, l.TLSConfig)
		if err != nil {
			l.IPv4HTTP.Close()
			l.IPv6HTTP.Close()
			l.IPv4HTTPS.Close()
			return nil, fmt.Errorf("IPv6 HTTPS listener on %s: %w", https6Addr, err)
		}
	}

	return l, nil
}

// CloseAll closes all open listeners.
func (l *Listeners) CloseAll() {
	if l.IPv4HTTP != nil {
		l.IPv4HTTP.Close()
	}
	if l.IPv6HTTP != nil {
		l.IPv6HTTP.Close()
	}
	if l.IPv4HTTPS != nil {
		l.IPv4HTTPS.Close()
	}
	if l.IPv6HTTPS != nil {
		l.IPv6HTTPS.Close()
	}
}
