FROM golang:1.24-alpine AS builder
WORKDIR /build
COPY go.mod .
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-s -w -X main.version=$(git describe --tags --always 2>/dev/null || echo dev)" \
    -o server ./cmd/server

FROM alpine:3.21
RUN apk add --no-cache ca-certificates curl
COPY --from=builder /build/server /server
EXPOSE 80 443
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s \
    CMD curl -sf http://localhost:80/health || exit 1
ENTRYPOINT ["/server"]
