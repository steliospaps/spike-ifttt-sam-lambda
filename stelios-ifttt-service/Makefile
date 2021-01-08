#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'
 
.PHONY: local-init local-run local-cleanup build local-run-build

LOCAL_AWS_PROFILE=fake
MARKER_LOCAL=.marker_local_init

local-init:     ## (re)inititalise local resources (such as dynamo db). remember to local-cleanup
	docker-compose up -d
	echo you can access the shell at http://localhost:8000/shell/
	echo using local aws profile '$(LOCAL_AWS_PROFILE)' If aws errors please set it up
	AWS_PROFILE=$(LOCAL_AWS_PROFILE) dynamodb/create-local-table.sh
	touch $(MARKER_LOCAL)

$(MARKER_LOCAL):
	$(MAKE) local-init

local-clean:  ## cleanup local resources (such as dynamo db)
	docker-compose down --volumes
	rm -f $(MARKER_LOCAL)

clean: ## cleanup everything
clean: local-clean
	rm -rf .aws-sam package

local-run:      ## start api with local dynamo db (local changes will be reflected without a build)
local-run: $(MARKER_LOCAL) package
	rm -rf .aws-sam #get rid of build so that changes will be reflected
	sam local start-api --docker-network=local-sam

local-run-build:      ## start api with local dynamo db (.aws-sam will be used or created. This means that local changes will not be reflected until 'aws-sam build' is run)
local-run-build: $(MARKER_LOCAL) package .aws-sam
	rm -rf .aws-sam #get rid of build so that changes will be reflected
	sam local start-api --docker-network=local-sam

package: ## refresh package dependencies
package: layer_package_requirements.txt
	rm -rf package
	pip install --target $@/python/lib/python3.8/site-packages/ -r $^
	
build: ## force a rebuild
.aws-sam: $(wildcard */*.py) template.yaml
build .aws-sam: package
	sam build


deploy: ## deploy app you need to provide API_KEY_SECRET_NAME (and AWS_PROFILE in the env)
deploy: .aws-sam
	test -n '$(API_KEY_SECRET_NAME)' # API_KEY_SECRET_NAME must be non empty
	sam deploy --parameter-overrides 'ApiKeySecretName=$(API_KEY_SECRET_NAME)'
	