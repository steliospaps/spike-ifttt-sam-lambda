#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

.PHONY: clean deploy build

clean: ## remove all generated artifacts
	./gradlew clean
	rm -rf ./bin

COPILOT_ENV=prod
COPILOT_SVC=backend
COPILOT_APP=stelios-ifttt-alerter-backend
COPILOT=copilot
AWS=aws

build: ## build java artifacts
	./gradlew clean test assemble

deploy: build
deploy: ## deploy the COPILOT_SVC app to the COPILOT_ENV environment. You might have to setup AWS_PROFILE before you invoke this
	$(COPILOT) svc deploy -n $(COPILOT_SVC) --env $(COPILOT_ENV) --app $(COPILOT_APP)

put-secret: ## set a secret in ssm for this app you have to set SECRET_NAME, SECRET_VALUE when you invoke make (they cannot contain the character ')
	test -n '$(SECRET_NAME)' #define SECRET_NAME
	test -n '$(SECRET_VALUE)' #define SECRET_NAME
	$(AWS) ssm put-parameter --name='$(SECRET_NAME)' --value='$(SECRET_VALUE)' --type SecureString \
		--tags Key=copilot-environment,Value=$(COPILOT_ENV) Key=copilot-application,Value=$(COPILOT_APP)