#https://gist.github.com/prwhite/8168133
# Add the following 'help' target to your Makefile
# And add help text after each target name starting with '\#\#'
 
help:           ## Show this help.
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'
 
.PHONY: local-init local-run local-cleanup build

LOCAL_AWS_PROFILE=fake
MARKER_LOCAL=.marker_local_init

local-init:     ## (re)inititalise local resources (such as dynamo db). remember to local-cleanup
	docker-compose up -d
	echo using local aws profile '$(LOCAL_AWS_PROFILE)' If aws errors please set it up
	AWS_PROFILE=$(LOCAL_AWS_PROFILE) dynamodb/create-local-table.sh
	touch $(MARKER_LOCAL)

$(MARKER_LOCAL):
	$(MAKE) local-init

local-cleanup:  ## cleanup local resources (such as dynamo db)
	docker-compose down --volumes
	rm -f $(MARKER_LOCAL)

local-run:      ## start api with local dynamo db
local-run: $(MARKER_LOCAL)
	echo removing build directory to allow live editing
	rm -rf .aws-sam
	sam local start-api --docker-network=local-sam

build:
	sam build

deploy: build
	test -n '$(API_KEY)' # API_KEY must be non empty
	test -n '$(AWS_PROFILE)' # AWS_PROFILE must be non empty
	AWS_PROFILE=$(AWS_PROFILE) sam deploy --parameter-overrides 'ApiKey=$(API_KEY)'
	