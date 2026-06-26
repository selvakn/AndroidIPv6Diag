BINARY      := server
BIN_DIR     := bin
CMD_PATH    := ./cmd/server
VERSION     ?= dev
LDFLAGS     := -ldflags "-s -w -X main.version=$(VERSION)"
BUILD_FLAGS := CGO_ENABLED=0

.PHONY: build build-arm64 run test vet clean

build:
	$(BUILD_FLAGS) GOOS=linux GOARCH=amd64 go build $(LDFLAGS) -o $(BIN_DIR)/$(BINARY) $(CMD_PATH)

build-arm64:
	$(BUILD_FLAGS) GOOS=linux GOARCH=arm64 go build $(LDFLAGS) -o $(BIN_DIR)/$(BINARY)-arm64 $(CMD_PATH)

run:
	go run $(CMD_PATH)

test:
	go test ./...

vet:
	go vet ./...

clean:
	rm -rf $(BIN_DIR)
