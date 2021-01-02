#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

.PHONY: clean deploy build

clean: ## remove all generated artifacts
	./gradlew clean
	rm -rf ./bin

AWS_PROFILE=ighackathon
COPILOT_ENV=prod
COPILOT_SVC=backend
COPILOT=copilot

build: ## build java artifacts
	./gradlew clean test assemble

deploy: build
deploy: ## deploy the COPILOT_SVC app to the COPILOT_ENV environment
	AWS_PROFILE=$(AWS_PROFILE) $(COPILOT) svc deploy -n $(COPILOT_SVC) --env $(COPILOT_ENV)
