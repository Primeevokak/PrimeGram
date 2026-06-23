LIBXRAY_REPO   := https://github.com/xtls/libxray
LIBXRAY_DIR    := /tmp/libxray
OUTPUT_AAR     := $(CURDIR)/libs/libXray.aar
ANDROID_API    := 21
GONOSUMDB      := *

.PHONY: all aar clean check-deps

all: aar

aar: check-deps $(LIBXRAY_DIR)
	@echo "→ Building libXray.aar (android/arm64 + arm)..."
	cd $(LIBXRAY_DIR) && \
		GONOSUMDB=$(GONOSUMDB) \
		gomobile bind \
			-target android \
			-androidapi $(ANDROID_API) \
			-o $(OUTPUT_AAR) \
			.
	@echo "✓ $(OUTPUT_AAR)"

$(LIBXRAY_DIR):
	git clone --depth=1 $(LIBXRAY_REPO) $(LIBXRAY_DIR)

clean:
	rm -f $(OUTPUT_AAR) $(dir $(OUTPUT_AAR))libXray-sources.jar

check-deps:
	@command -v go >/dev/null || (echo "ERROR: go not found"; exit 1)
	@command -v gomobile >/dev/null || { \
		echo "→ Installing gomobile..."; \
		GONOSUMDB=$(GONOSUMDB) go install golang.org/x/mobile/cmd/gomobile@latest; \
	}
	@test -n "$(ANDROID_HOME)" || (echo "ERROR: ANDROID_HOME not set (source /etc/profile)"; exit 1)
	@# gomobile requires golang.org/x/mobile/bind to be available in module deps
	@cd $(LIBXRAY_DIR) && GONOSUMDB=$(GONOSUMDB) go get golang.org/x/mobile/bind 2>/dev/null || true
	@gomobile init
	@# gomobile needs android-<API> dir in platforms; create symlink if missing
	@test -d $(ANDROID_HOME)/platforms/android-$(ANDROID_API) || { \
		LATEST=$$(ls $(ANDROID_HOME)/platforms/ | sort -V | tail -1); \
		echo "→ Symlinking platforms/$$LATEST → android-$(ANDROID_API)"; \
		sudo ln -s $(ANDROID_HOME)/platforms/$$LATEST $(ANDROID_HOME)/platforms/android-$(ANDROID_API); \
	}
	@echo "✓ deps OK  (go=$$(go version | awk '{print $$3}'))"
